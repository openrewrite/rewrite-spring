/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.data.search;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.spring.table.MongoValueRepresentationFields;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.trait.Comments;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.openrewrite.java.spring.data.search.FindMissingMongoValueRepresentation.*;

final class MongoValueRepresentationDiagnostics {

    private MongoValueRepresentationDiagnostics() {
    }

    static SourceFile apply(FindMissingMongoValueRepresentation recipe, SourceFile source, JavaProject project,
                            Accumulator acc, ExecutionContext ctx) {
        if (acc.finalizedProjects.contains(project)) {
            return source;
        }
        ProjectDiagnosis diagnosis = diagnose(project, acc);
        if (diagnosis == null) {
            return source;
        }
        insertRowsOnce(recipe, project, diagnosis.actionable, acc, ctx);

        if (source instanceof JavaSourceFile) {
            return applyToJavaConfiguration((JavaSourceFile) source, project, diagnosis, acc, ctx);
        }
        if (diagnosis.preferredConfiguration == null ||
                (!(source instanceof Properties.File) && !(source instanceof Yaml.Documents))) {
            return source;
        }
        return applyToConfigurationFile(source, project, diagnosis, acc, ctx);
    }

    /**
     * Marks any faulty Java configuration calls (e.g. {@code uuidRepresentation(null)}) in place,
     * rather than leaving them to be silently shadowed by a properties-file suggestion for the same
     * kind.
     */
    private static SourceFile applyToJavaConfiguration(JavaSourceFile source, JavaProject project,
                                                        ProjectDiagnosis diagnosis, Accumulator acc,
                                                        ExecutionContext ctx) {
        List<ConfigurationIssue> issues = configurationIssues(
                source.getSourcePath(), project, diagnosis.occurrences, acc);
        if (issues.isEmpty()) {
            return source;
        }
        SourceFile changed = markJavaConfigurationIssues(source, issues, ctx);
        return finalizeUnless(changed, needsBaselineFile(diagnosis, project, acc));
    }

    private static SourceFile markJavaConfigurationIssues(JavaSourceFile source, List<ConfigurationIssue> issues,
                                                           ExecutionContext ctx) {
        Map<UUID, ValueKind> kinds = new HashMap<>();
        for (ConfigurationIssue issue : issues) {
            kinds.put(issue.getTreeId(), issue.getKind());
        }
        // The flagged invocation itself is left untouched, since wrapping it in a SearchResult
        // marker would print as literal text ahead of the real statement in a production run,
        // corrupting it (SearchResult is only kept separate from real output inside the RewriteTest
        // harness); Comments.of(...) is idempotent, so no separate already-commented guard is needed.
        return (SourceFile) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation m = super.visitMethodInvocation(method, p);
                ValueKind kind = kinds.get(m.getId());
                return kind == null ? m :
                        Comments.of(new Cursor(getCursor().getParentOrThrow(), m)).comment(" " + kind.invalidPropertyMessage);
            }
        }.visitNonNull(source, ctx);
    }

    /**
     * Whether some kind still needs a baseline configuration file: unresolved, with no config file
     * for this project yet, and never attempted in Java either (a faulty Java attempt is handled by
     * {@link #applyToJavaConfiguration} instead of a competing properties-file suggestion).
     */
    private static boolean needsBaselineFile(ProjectDiagnosis diagnosis, JavaProject project, Accumulator acc) {
        if (diagnosis.preferredConfiguration != null) {
            return false;
        }
        for (Occurrence occurrence : diagnosis.unresolved) {
            if (acc.isJavaUnattempted(occurrence.getKind(), project)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A baseline {@code application.properties} for a project that has affected fields but no
     * Spring configuration file, so every affected field gets the same suggested-property
     * treatment on a later cycle instead of an arbitrary class being singled out.
     */
    static @Nullable SourceFile generateBaselineConfiguration(JavaProject project, Accumulator acc,
                                                               ExecutionContext ctx) {
        Path path = baselineConfigurationPath(project, acc);
        if (path == null) {
            return null;
        }
        Optional<SourceFile> parsed = PropertiesParser.builder().build().parse(ctx, "").findFirst();
        if (!parsed.isPresent()) {
            return null;
        }
        SourceFile brandNewFile = parsed.get().withSourcePath(path);
        return brandNewFile.withMarkers(brandNewFile.getMarkers()
                .addIfAbsent(project)
                .addIfAbsent(new RowsRecorded(Tree.randomId())));
    }

    private static @Nullable Path baselineConfigurationPath(JavaProject project, Accumulator acc) {
        ProjectDiagnosis diagnosis = diagnose(project, acc);
        if (diagnosis == null || !needsBaselineFile(diagnosis, project, acc)) {
            return null;
        }
        ConcurrentLinkedQueue<Occurrence> projectOccurrences = acc.occurrences.get(project);
        if (projectOccurrences == null) {
            return null;
        }
        for (Occurrence occurrence : projectOccurrences) {
            Path resourcesRoot = resourcesRootFor(occurrence.getSourcePath());
            if (resourcesRoot != null) {
                return resourcesRoot.resolve("application.properties");
            }
        }
        return null;
    }

    private static @Nullable Path resourcesRootFor(Path javaSourcePath) {
        Path parent = javaSourcePath.getParent();
        if (parent == null) {
            return null;
        }
        int count = parent.getNameCount();
        for (int i = 0; i + 2 < count; i++) {
            if ("src".equals(parent.getName(i).toString()) &&
                    "main".equals(parent.getName(i + 1).toString()) &&
                    "java".equals(parent.getName(i + 2).toString())) {
                Path resources = Paths.get("src", "main", "resources");
                return i == 0 ? resources : parent.subpath(0, i).resolve(resources);
            }
        }
        return null;
    }

    /**
     * What, if anything, this project needs done: which occurrences still need a configured
     * representation, and where (if anywhere) a new configuration should be suggested. Independent
     * of which source file is currently being visited.
     */
    private static @Nullable ProjectDiagnosis diagnose(JavaProject project, Accumulator acc) {
        List<Occurrence> occurrences = projectOccurrences(project, acc);
        if (occurrences.isEmpty()) {
            return null;
        }
        List<Occurrence> unresolved = unresolvedOccurrences(occurrences, project, acc);
        Set<ValueKind> invalidKinds = invalidConfigurationKinds(project, acc);
        List<Occurrence> actionable = actionableOccurrences(occurrences, unresolved, invalidKinds);
        if (actionable.isEmpty()) {
            return null;
        }
        Path preferredConfiguration = acc.preferredConfigurationSource.get(project);
        return new ProjectDiagnosis(occurrences, unresolved, actionable, preferredConfiguration);
    }

    private static SourceFile applyToConfigurationFile(SourceFile source, JavaProject project,
                                                        ProjectDiagnosis diagnosis, Accumulator acc,
                                                        ExecutionContext ctx) {
        List<ConfigurationIssue> issues = configurationIssues(
                source.getSourcePath(), project, diagnosis.occurrences, acc);
        boolean preferred = source.getSourcePath().equals(diagnosis.preferredConfiguration);
        List<ValueKind> propertiesToAdd = propertiesToAdd(preferred, diagnosis.unresolved, project, acc);
        if (issues.isEmpty() && propertiesToAdd.isEmpty()) {
            return source;
        }

        SourceFile changed = addSuggestedProperties(source, propertiesToAdd, ctx);
        if (!issues.isEmpty()) {
            changed = markConfigurationIssues(changed, issues, ctx);
        }
        return changed.withMarkers(changed.getMarkers().addIfAbsent(new ProjectDiagnostic(Tree.randomId())));
    }

    /**
     * Attaches the "fully handled" marker unless {@code stillPending} — a baseline configuration
     * file may still be needed for some other kind, and that generate-then-populate sequence takes
     * multiple cycles; finalizing here first would permanently stop it from ever completing.
     */
    private static SourceFile finalizeUnless(SourceFile changed, boolean stillPending) {
        return stillPending ? changed :
                changed.withMarkers(changed.getMarkers().addIfAbsent(new ProjectDiagnostic(Tree.randomId())));
    }

    private static SourceFile addSuggestedProperties(SourceFile source, List<ValueKind> propertiesToAdd,
                                                     ExecutionContext ctx) {
        SourceFile changed = source;
        for (ValueKind kind : propertiesToAdd) {
            changed = addUnspecifiedPropertySuggestion(changed, kind, ctx);
        }
        return changed;
    }

    private static final class ProjectDiagnosis {
        final List<Occurrence> occurrences;
        final List<Occurrence> unresolved;
        final List<Occurrence> actionable;
        final @Nullable Path preferredConfiguration;

        ProjectDiagnosis(List<Occurrence> occurrences, List<Occurrence> unresolved, List<Occurrence> actionable,
                         @Nullable Path preferredConfiguration) {
            this.occurrences = occurrences;
            this.unresolved = unresolved;
            this.actionable = actionable;
            this.preferredConfiguration = preferredConfiguration;
        }
    }

    private static List<Occurrence> projectOccurrences(JavaProject project, Accumulator acc) {
        return new ArrayList<>(acc.occurrences.getOrDefault(project, new ConcurrentLinkedQueue<>()));
    }

    private static List<Occurrence> unresolvedOccurrences(List<Occurrence> occurrences, JavaProject project,
                                                           Accumulator acc) {
        List<Occurrence> unresolved = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            if (acc.isUnconfigured(occurrence.getKind(), project)) {
                unresolved.add(occurrence);
            }
        }
        return unresolved;
    }

    private static Set<ValueKind> invalidConfigurationKinds(JavaProject project, Accumulator acc) {
        Set<ValueKind> kinds = EnumSet.noneOf(ValueKind.class);
        for (ConfigurationIssue issue : acc.configurationIssues.getOrDefault(project,
                new ConcurrentLinkedQueue<>())) {
            kinds.add(issue.getKind());
        }
        return kinds;
    }

    private static List<Occurrence> actionableOccurrences(List<Occurrence> occurrences,
                                                           List<Occurrence> unresolved,
                                                           Set<ValueKind> invalidKinds) {
        if (invalidKinds.isEmpty()) {
            return unresolved;
        }
        // A kind can be configured overall (a valid value exists somewhere) yet still have a
        // separate invalid entry elsewhere, e.g., a bad profile override alongside a valid default.
        // Such occurrences aren't in `unresolved`, so invalidKinds is what surfaces them here.
        Set<Occurrence> unresolvedLookup = new HashSet<>(unresolved);
        List<Occurrence> actionable = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            if (unresolvedLookup.contains(occurrence) || invalidKinds.contains(occurrence.getKind())) {
                actionable.add(occurrence);
            }
        }
        return actionable;
    }

    private static List<ValueKind> propertiesToAdd(boolean preferred, List<Occurrence> unresolved,
                                                    JavaProject project, Accumulator acc) {
        List<ValueKind> properties = new ArrayList<>();
        if (preferred) {
            for (ValueKind kind : ValueKind.values()) {
                if (hasKind(unresolved, kind) && acc.isPropertyUnattempted(kind, project) && acc.isJavaUnattempted(kind, project)) {
                    properties.add(kind);
                }
            }
        }
        return properties;
    }

    private static void insertRowsOnce(FindMissingMongoValueRepresentation recipe, JavaProject project,
                                       List<Occurrence> occurrences, Accumulator acc, ExecutionContext ctx) {
        if (!acc.rowsInsertedProjects.add(project)) {
            return;
        }
        for (Occurrence occurrence : occurrences) {
            recipe.affectedFields.insertRow(ctx, new MongoValueRepresentationFields.Row(
                    occurrence.getSourcePath().toString(), occurrence.getOwningType(), occurrence.getField(),
                    occurrence.getKind().displayName, occurrence.getKind().configurationProperty));
        }
    }

    /**
     * {@link FindMissingMongoValueRepresentation#UNSPECIFIED_VALUE}: a real, validly bindable value
     * rather than placeholder text, so a project that never gets its suggestion followed up on still
     * starts up correctly — it's simply as unconfigured as it was before the recipe ran. Uses the
     * same message an existing invalid value would get from {@link #markConfigurationIssues}, since
     * that's exactly what this value is once written.
     */
    private static SourceFile addUnspecifiedPropertySuggestion(SourceFile source, ValueKind kind, ExecutionContext ctx) {
        String path = source.getSourcePath().toString().replace('\\', '/');
        return (SourceFile) new AddSpringProperty(
                kind.configurationProperty, UNSPECIFIED_VALUE, kind.invalidPropertyMessage, Collections.singletonList(path))
                .getVisitor().visitNonNull(source, ctx);
    }

    private static List<ConfigurationIssue> configurationIssues(Path sourcePath, JavaProject project,
                                                                List<Occurrence> occurrences, Accumulator acc) {
        List<ConfigurationIssue> issues = new ArrayList<>();
        for (ConfigurationIssue issue : acc.configurationIssues.getOrDefault(project,
                new ConcurrentLinkedQueue<>())) {
            if (issue.getSourcePath().equals(sourcePath) && hasKind(occurrences, issue.getKind())) {
                issues.add(issue);
            }
        }
        return issues;
    }

    private static SourceFile markConfigurationIssues(SourceFile source, List<ConfigurationIssue> issues,
                                                       ExecutionContext ctx) {
        Map<UUID, ValueKind> kinds = new HashMap<>();
        for (ConfigurationIssue issue : issues) {
            kinds.put(issue.getTreeId(), issue.getKind());
        }
        if (source instanceof Properties.File) {
            return (SourceFile) new PropertiesIsoVisitor<ExecutionContext>() {
                @Override
                public Properties.File visitFile(Properties.File file, ExecutionContext p) {
                    // Comments are sibling content in a Properties.File (not entry-prefix metadata),
                    // so — mirroring org.openrewrite.properties.AddPropertyComment — each match is
                    // commented directly, leaving the flagged entry itself untouched (see
                    // markJavaConfigurationIssues for why that matters).
                    Properties.File withComments = file;
                    for (Properties.Content content : file.getContent()) {
                        ValueKind kind = kinds.get(content.getId());
                        if (kind != null) {
                            withComments = Comments.of(new Cursor(
                                    new Cursor(getCursor().getParentOrThrow(), withComments), content))
                                    .comment(" " + kind.invalidPropertyMessage);
                        }
                    }
                    return withComments;
                }
            }.visitNonNull(source, ctx);
        }
        return (SourceFile) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext p) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, p);
                ValueKind kind = kinds.get(e.getValue().getId());
                return kind == null ? e :
                        Comments.of(new Cursor(getCursor().getParentOrThrow(), e)).comment(" " + kind.invalidPropertyMessage);
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasKind(List<Occurrence> occurrences, ValueKind kind) {
        return occurrences.stream().anyMatch(occurrence -> occurrence.getKind() == kind);
    }
}

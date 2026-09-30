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
package org.openrewrite.java.spring.data;

import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.spring.data.MongoValueRepresentationAccumulator.ConfigurationIssue;
import org.openrewrite.java.spring.data.MongoValueRepresentationAccumulator.Occurrence;
import org.openrewrite.java.spring.data.search.FindMissingMongoValueRepresentation;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.openrewrite.java.spring.data.MongoValueRepresentationAccumulator.hasKind;

/**
 * Adds explicit value-representation properties that preserve the Spring Data MongoDB 4 defaults
 * for MongoDB-mapped UUID/BigDecimal/BigInteger fields found by
 * {@link FindMissingMongoValueRepresentation}. Existing invalid configuration is flagged in place
 * rather than overwritten.
 */
public class AddMongoValueRepresentationProperty extends ScanningRecipe<MongoValueRepresentationAccumulator> {

    @Getter
    final String displayName = "Add MongoDB value representation property";

    @Getter
    final String description = "Add a value-representation property for MongoDB-mapped UUID, BigInteger, and BigDecimal fields " +
            "that require one when migrating to Spring Data MongoDB 5, preserving the Spring Data MongoDB 4 defaults for " +
            "previously unconfigured applications and flagging existing invalid configuration.";

    @Override
    public MongoValueRepresentationAccumulator getInitialValue(ExecutionContext ctx) {
        return new MongoValueRepresentationAccumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(MongoValueRepresentationAccumulator acc) {
        return MongoValueRepresentationScanner.scanner(acc);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(MongoValueRepresentationAccumulator acc) {
        TreeVisitor<?, ExecutionContext> javaVisitor = Preconditions.check(
                MongoValueRepresentationScanner.javaConfigurationPrecondition(), javaConfigurationVisitor(acc));
        PropertiesIsoVisitor<ExecutionContext> propertiesVisitor = propertiesConfigurationVisitor(acc);
        YamlIsoVisitor<ExecutionContext> yamlVisitor = yamlConfigurationVisitor(acc);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof J.CompilationUnit) {
                    return javaVisitor.visit(tree, ctx);
                }
                if (tree instanceof Properties.File) {
                    return propertiesVisitor.visit(tree, ctx);
                }
                if (tree instanceof Yaml.Documents) {
                    return yamlVisitor.visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    /**
     * Marks faulty Java configuration calls in place, rather than shadowing them with a
     * properties-file suggestion for the same kind.
     */
    private static JavaIsoVisitor<ExecutionContext> javaConfigurationVisitor(MongoValueRepresentationAccumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                JavaProject project = SpringConfigFileSupport.javaProject(cu);
                return project == null ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visitedMethod = super.visitMethodInvocation(method, ctx);
                JavaSourceFile source = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);
                JavaProject project = SpringConfigFileSupport.javaProject(source);
                MongoValueRepresentationKind kind = configurationIssueKind(acc, project, source.getSourcePath(), visitedMethod.getId());
                // Leave the flagged invocation untouched: a SearchResult marker would print as literal text
                // corrupting production output. Comments.of(...) is idempotent, so no already-commented guard
                // is needed even though every cycle re-detects the same faulty call as an issue.
                return kind == null ? visitedMethod :
                        Comments.of(new Cursor(getCursor().getParentOrThrow(), visitedMethod)).comment(" " + kind.invalidPropertyMessage);
            }
        };
    }

    private static @Nullable MongoValueRepresentationKind configurationIssueKind(
            MongoValueRepresentationAccumulator acc, @Nullable JavaProject project, Path sourcePath, UUID treeId) {
        if (project == null) {
            return null;
        }
        for (ConfigurationIssue issue : acc.configurationIssuesIn(sourcePath, project, acc.projectOccurrences(project))) {
            if (issue.getTreeId().equals(treeId)) {
                return issue.getKind();
            }
        }
        return null;
    }

    private static PropertiesIsoVisitor<ExecutionContext> propertiesConfigurationVisitor(MongoValueRepresentationAccumulator acc) {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.File visitFile(Properties.File file, ExecutionContext ctx) {
                Diagnosis diagnosis = diagnose(file, acc);
                if (diagnosis == null) {
                    return file;
                }
                Properties.File changed = file;
                for (MongoValueRepresentationKind kind : diagnosis.propertiesToAdd) {
                    changed = (Properties.File) addSpringData4DefaultProperty(changed, kind, ctx);
                }
                return diagnosis.issues.isEmpty() ? changed :
                        markPropertiesConfigurationIssues(changed, diagnosis.issues, getCursor().getParentOrThrow());
            }
        };
    }

    private static YamlIsoVisitor<ExecutionContext> yamlConfigurationVisitor(MongoValueRepresentationAccumulator acc) {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                Diagnosis diagnosis = diagnose(documents, acc);
                if (diagnosis == null) {
                    return documents;
                }
                Yaml.Documents changed = documents;
                for (MongoValueRepresentationKind kind : diagnosis.propertiesToAdd) {
                    changed = (Yaml.Documents) addSpringData4DefaultProperty(changed, kind, ctx);
                }
                return diagnosis.issues.isEmpty() ? changed : markYamlConfigurationIssues(changed, diagnosis.issues, ctx);
            }
        };
    }

    @Value
    private static class Diagnosis {
        List<ConfigurationIssue> issues;
        List<MongoValueRepresentationKind> propertiesToAdd;
    }

    /**
     * What (if anything) this configuration file needs done: existing invalid values to flag, and/or
     * default-preserving properties to add if it's the project's preferred configuration source. Null when
     * there's nothing for this file — no project marker, no affected fields, or nothing actionable.
     */
    private static @Nullable Diagnosis diagnose(SourceFile source, MongoValueRepresentationAccumulator acc) {
        JavaProject project = SpringConfigFileSupport.javaProject(source);
        if (project == null) {
            return null;
        }
        List<Occurrence> occurrences = acc.projectOccurrences(project);
        if (occurrences.isEmpty()) {
            return null;
        }
        List<ConfigurationIssue> issues = acc.configurationIssuesIn(source.getSourcePath(), project, occurrences);
        List<MongoValueRepresentationKind> propertiesToAdd = propertiesToAddTo(source.getSourcePath(), acc, project);
        return issues.isEmpty() && propertiesToAdd.isEmpty() ? null : new Diagnosis(issues, propertiesToAdd);
    }

    /**
     * A file only receives new defaults when it's the project's preferred configuration source.
     * Only base {@code application.*} files are ever recorded as preferred — a profile-specific file
     * only loads conditionally, so a value placed there wouldn't protect every other profile.
     */
    private static List<MongoValueRepresentationKind> propertiesToAddTo(Path sourcePath, MongoValueRepresentationAccumulator acc,
                                                                          JavaProject project) {
        return sourcePath.equals(acc.preferredConfigurationSource(project)) ? propertiesToAdd(acc, project) : Collections.emptyList();
    }

    private static List<MongoValueRepresentationKind> propertiesToAdd(MongoValueRepresentationAccumulator acc, JavaProject project) {
        List<Occurrence> unresolved = acc.unresolvedOccurrences(project);
        List<MongoValueRepresentationKind> properties = new ArrayList<>();
        for (MongoValueRepresentationKind kind : MongoValueRepresentationKind.values()) {
            if (hasKind(unresolved, kind) && acc.isPropertyUnattempted(kind, project) &&
                    acc.isJavaUnattempted(kind, project)) {
                properties.add(kind);
            }
        }
        return properties;
    }

    /**
     * Generates a baseline {@code application.properties} for a project with affected fields but no
     * base Spring configuration file — only a profile-specific one, or none at all — fully populated
     * up front with the Spring Data MongoDB 4 defaults so no further cycle is needed to finish it off.
     */
    @Override
    public Collection<? extends SourceFile> generate(MongoValueRepresentationAccumulator acc, ExecutionContext ctx) {
        List<SourceFile> generated = new ArrayList<>();
        for (JavaProject project : acc.projectsWithOccurrences()) {
            SourceFile baseline = generateBaselineConfiguration(project, acc, ctx);
            if (baseline != null) {
                generated.add(baseline);
            }
        }
        return generated;
    }

    private static @Nullable SourceFile generateBaselineConfiguration(JavaProject project, MongoValueRepresentationAccumulator acc,
                                                                        ExecutionContext ctx) {
        if (acc.preferredConfigurationSource(project) != null) {
            // A base configuration file already exists for this project; propertiesConfigurationVisitor/yamlConfigurationVisitor handle it directly.
            return null;
        }
        List<MongoValueRepresentationKind> kindsNeedingSuggestion = propertiesToAdd(acc, project);
        if (kindsNeedingSuggestion.isEmpty()) {
            return null;
        }
        Path path = baselineConfigurationPath(project, acc);
        if (path == null) {
            return null;
        }
        Optional<SourceFile> parsed = PropertiesParser.builder().build().parse(ctx, "").findFirst();
        if (!parsed.isPresent()) {
            return null;
        }
        SourceFile file = parsed.get().withSourcePath(path);
        file = file.withMarkers(file.getMarkers().addIfAbsent(project));
        for (MongoValueRepresentationKind kind : kindsNeedingSuggestion) {
            file = addSpringData4DefaultProperty(file, kind, ctx);
        }
        return file;
    }

    private static @Nullable Path baselineConfigurationPath(JavaProject project, MongoValueRepresentationAccumulator acc) {
        for (Occurrence occurrence : acc.projectOccurrences(project)) {
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
     * Adds the concrete representation used implicitly by Spring Data MongoDB 4. No diagnostic is
     * needed when configuration was absent because the inserted value preserves the framework's
     * previous default.
     */
    private static SourceFile addSpringData4DefaultProperty(SourceFile source, MongoValueRepresentationKind kind, ExecutionContext ctx) {
        String path = source.getSourcePath().toString().replace('\\', '/');
        return (SourceFile) new AddSpringProperty(
                kind.configurationProperty, kind.springData4DefaultValue, null, Collections.singletonList(path))
                .getVisitor().visitNonNull(source, ctx);
    }

    // A Properties.File has no per-entry "leading comment" field — comments and entries are
    // siblings in one flat content list. So, as org.openrewrite.properties.AddPropertyComment
    // also does, a comment is attached to the matching content directly, leaving the entry itself
    // unmodified.
    private static Properties.File markPropertiesConfigurationIssues(Properties.File file, List<ConfigurationIssue> issues,
                                                                       Cursor fileParentCursor) {
        Map<UUID, MongoValueRepresentationKind> kinds = kindsByTreeId(issues);
        Properties.File withComments = file;
        for (Properties.Content content : file.getContent()) {
            MongoValueRepresentationKind kind = kinds.get(content.getId());
            if (kind != null) {
                withComments = Comments.of(new Cursor(new Cursor(fileParentCursor, withComments), content))
                        .comment(" " + kind.invalidPropertyMessage);
            }
        }
        return withComments;
    }

    private static Yaml.Documents markYamlConfigurationIssues(Yaml.Documents documents, List<ConfigurationIssue> issues,
                                                                ExecutionContext ctx) {
        Map<UUID, MongoValueRepresentationKind> kinds = kindsByTreeId(issues);
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext entryContext) {
                Yaml.Mapping.Entry visitedEntry = super.visitMappingEntry(entry, entryContext);
                MongoValueRepresentationKind kind = kinds.get(visitedEntry.getValue().getId());
                return kind == null ? visitedEntry :
                        Comments.of(new Cursor(getCursor().getParentOrThrow(), visitedEntry)).comment(" " + kind.invalidPropertyMessage);
            }
        }.visitNonNull(documents, ctx);
    }

    private static Map<UUID, MongoValueRepresentationKind> kindsByTreeId(List<ConfigurationIssue> issues) {
        Map<UUID, MongoValueRepresentationKind> kinds = new HashMap<>();
        for (ConfigurationIssue issue : issues) {
            kinds.put(issue.getTreeId(), issue.getKind());
        }
        return kinds;
    }
}

/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot2.search;

import lombok.Data;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.semver.DependencyMatcher;
import org.openrewrite.trait.Comments;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;

public class FindIntegrationSchedulerPool extends ScanningRecipe<FindIntegrationSchedulerPool.JavaProjects> {

    private static final Pattern APP_PROPS_FILE_REGEX = Pattern.compile("^application.*\\.properties$");
    private static final Pattern APP_YAML_FILE_REGEX = Pattern.compile("^application.*\\.ya?ml$");

    private static final String PROPERTY_KEY = "spring.task.scheduling.pool.size";

    private static final String PROPS_MIGRATION_MESSAGE = " TODO: Consider Scheduler thread pool size for Spring Integration";
    private static final String GENERAL_MIGRATION_MESSAGE = " TODO: Scheduler thread pool size for Spring Integration either in properties or config server";
    private static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";

    @Getter
    final String displayName = "Integration scheduler pool size";

    @Getter
    final String description = "Spring Integration now reuses an available `TaskScheduler` rather than configuring its own. In a " +
            "typical application setup relying on the auto-configuration, this means that Spring Integration " +
            "uses the auto-configured task scheduler that has a pool size of 1. To restore Spring Integration’s " +
            "default of 10 threads, use the `spring.task.scheduling.pool.size` property.";

    private boolean isApplicableMavenProject(Xml.Document maven) {
        DependencyMatcher boot25Matcher = DependencyMatcher.build("org.springframework.boot:spring-boot:2.4.X").getValue();
        DependencyMatcher integrationMatcher = DependencyMatcher.build("org.springframework.integration:spring-integration-core").getValue();

        List<ResolvedDependency> deps = maven.getMarkers().findFirst(MavenResolutionResult.class)
                .orElseThrow(() -> new IllegalStateException("Maven visitors should not be visiting XML documents without a Maven marker"))
                .getDependencies().getOrDefault(Scope.Compile, emptyList());

        boolean boot25 = false;
        boolean si = false;

        for (ResolvedDependency d : deps) {
            if (!boot25) {
                assert boot25Matcher != null;
                boot25 = boot25Matcher.matches(d.getGroupId(), d.getArtifactId(), d.getVersion());
            }
            if (!si) {
                assert integrationMatcher != null;
                si = integrationMatcher.matches(d.getGroupId(), d.getArtifactId());
            }
            if (boot25 && si) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesSpringBootApplication(JavaSourceFile javaSourceFile) {
        return javaSourceFile.getTypesInUse().getTypesInUse().stream().anyMatch(t -> t instanceof
                JavaType.Class && ((JavaType.Class) t).getFullyQualifiedName().equals(SPRING_BOOT_APPLICATION));
    }

    private static boolean hasMigrationComment(SourceFile source) {
        if (source instanceof Properties.File) {
            return ((Properties.File) source).getContent().stream().anyMatch(c ->
                    c instanceof Properties.Comment && PROPS_MIGRATION_MESSAGE.equals(((Properties.Comment) c).getMessage()));
        } else if (source instanceof Yaml.Documents) {
            return new YamlIsoVisitor<AtomicBoolean>() {
                @Override
                public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, AtomicBoolean found) {
                    if (entry.getPrefix().contains("#" + PROPS_MIGRATION_MESSAGE)) {
                        found.set(true);
                    }
                    return super.visitMappingEntry(entry, found);
                }
            }.reduce(source, new AtomicBoolean()).get();
        } else if (source instanceof JavaSourceFile && usesSpringBootApplication((JavaSourceFile) source)) {
            // Look in every space, as a re-parse moves the comment from the annotation to the class declaration
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public Space visitSpace(Space space, Space.Location loc, AtomicBoolean found) {
                    if (space.getComments().stream().anyMatch(c -> c instanceof TextComment &&
                            GENERAL_MIGRATION_MESSAGE.trim().equals(((TextComment) c).getText().trim()))) {
                        found.set(true);
                    }
                    return space;
                }
            }.reduce(source, new AtomicBoolean()).get();
        }
        return false;
    }

    @Override
    public JavaProjects getInitialValue(ExecutionContext ctx) {
        return new JavaProjects();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(JavaProjects acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                if (hasMigrationComment((SourceFile) tree)) {
                    // already processed in a previous cycle or run
                    tree.getMarkers().findFirst(JavaProject.class).ifPresent(acc.getProcessedProjects()::add);
                    tree.getMarkers().findFirst(JavaProject.class).ifPresent(acc.getApplicableProjects()::remove);
                    return tree;
                }

                Optional<JavaProject> maybeJavaProject = tree.getMarkers().findFirst(JavaProject.class);
                if (!maybeJavaProject.isPresent() || acc.getProcessedProjects().contains(maybeJavaProject.get())) {
                    return tree;
                }

                SourceFile source = (SourceFile) tree;
                String fileName = source.getSourcePath().getFileName().toString();
                JavaProject javaProject = maybeJavaProject.get();

                if (source instanceof Xml.Document) {
                    Xml.Document xml = (Xml.Document) source;
                    Optional<MavenResolutionResult> maybeMavenMarker = source.getMarkers().findFirst(MavenResolutionResult.class);
                    if (maybeMavenMarker.isPresent() && isApplicableMavenProject(xml)) {
                        acc.getApplicableProjects().add(javaProject);
                    }
                } else if (source instanceof Properties && APP_PROPS_FILE_REGEX.matcher(fileName).matches()) {
                    if (!FindProperties.find((Properties) source, PROPERTY_KEY, false).isEmpty()) {
                        acc.getSourceToCommentByProject().put(javaProject, source.getSourcePath());
                    }
                } else if (source instanceof Yaml.Documents && APP_YAML_FILE_REGEX.matcher(fileName).matches()) {
                    if (!FindProperty.find((Yaml) source, PROPERTY_KEY, false).isEmpty()) {
                        acc.getSourceToCommentByProject().put(javaProject, source.getSourcePath());
                    }
                } else if (source instanceof JavaSourceFile && acc.getSourceToCommentByProject().get(javaProject) == null) {
                    JavaSourceFile javaSourceFile = (JavaSourceFile) source;
                    if (usesSpringBootApplication(javaSourceFile)) {
                        new JavaIsoVisitor<Integer>() {
                            final AnnotationMatcher annotationMatcher = new AnnotationMatcher('@' + SPRING_BOOT_APPLICATION);

                            @Override
                            public J.Annotation visitAnnotation(J.Annotation annotation, Integer p) {
                                if (annotationMatcher.matches(annotation)) {
                                    acc.getSourceToCommentByProject().put(javaProject, source.getSourcePath());
                                }
                                return annotation;
                            }
                        }.visit(javaSourceFile, 0);
                    }
                }
                return source;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(JavaProjects acc) {
        Set<Path> sourcesToComment = acc.getSourceToCommentByProject().entrySet().stream()
                .filter(e -> acc.getApplicableProjects().contains(e.getKey()))
                .map(Map.Entry::getValue).collect(toSet());

        if (sourcesToComment.isEmpty()) {
            return TreeVisitor.noop();
        }

        // Leave the comment about scheduler pool size next to 'spring.task.scheduling.pool.size' property in the external properties file (props or yaml)
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile) || !sourcesToComment.contains(((SourceFile) tree).getSourcePath()) ||
                        hasMigrationComment((SourceFile) tree)) {
                    return tree;
                }

                SourceFile source = (SourceFile) tree;
                if (source instanceof Properties) {
                    Set<Properties.Entry> foundEntries = FindProperties.find((Properties) source, PROPERTY_KEY, false);
                    if (!foundEntries.isEmpty()) {
                        // There should only be one exact match!
                        Properties.Entry entry = foundEntries.iterator().next();
                        source = Comments.of(new Cursor(new Cursor(getCursor(), source), entry)).comment(PROPS_MIGRATION_MESSAGE);
                    }
                } else if (source instanceof Yaml) {
                    Set<Yaml.Block> foundEntriesValues = FindProperty.find((Yaml) source, PROPERTY_KEY, false);
                    if (!foundEntriesValues.isEmpty()) {
                        source = (SourceFile) new YamlIsoVisitor<ExecutionContext>() {
                            @Override
                            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                                if (foundEntriesValues.contains(entry.getValue())) {
                                    entry = Comments.of(getCursor()).comment(PROPS_MIGRATION_MESSAGE);
                                }
                                return super.visitMappingEntry(entry, ctx);
                            }
                        }.visitNonNull(source, ctx);
                    }
                } else if (source instanceof JavaSourceFile) {
                    JavaIsoVisitor<ExecutionContext> commentVisitor = new JavaIsoVisitor<ExecutionContext>() {
                        final AnnotationMatcher annotationMatcher = new AnnotationMatcher('@' + SPRING_BOOT_APPLICATION);

                        @Override
                        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                            if (classDecl.getAllAnnotations().stream().anyMatch(annotationMatcher::matches)) {
                                return Comments.of(getCursor()).comment(GENERAL_MIGRATION_MESSAGE, Comments.Placement.BEFORE, "\n" + classDecl.getPrefix().getIndent());
                            }
                            return super.visitClassDeclaration(classDecl, ctx);
                        }
                    };
                    source = (SourceFile) commentVisitor.visitNonNull(source, ctx);
                }
                return source;
            }
        };
    }

    @Data
    static class JavaProjects {
        Set<JavaProject> applicableProjects = new HashSet<>();
        Set<JavaProject> processedProjects = new HashSet<>();
        Map<JavaProject, Path> sourceToCommentByProject = new HashMap<>();
    }
}

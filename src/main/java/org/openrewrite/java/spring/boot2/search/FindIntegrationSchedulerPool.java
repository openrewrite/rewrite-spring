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
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
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
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

public class FindIntegrationSchedulerPool extends ScanningRecipe<FindIntegrationSchedulerPool.JavaProjects> {

    private static final Pattern APP_PROPS_FILE_REGEX = Pattern.compile("^application.*\\.properties$");
    private static final Pattern APP_YAML_FILE_REGEX = Pattern.compile("^application.*\\.ya?ml$");

    private static final DependencyMatcher SPRING_BOOT_24 = requireNonNull(
            DependencyMatcher.build("org.springframework.boot:spring-boot:2.4.X").getValue());
    private static final String SPRING_INTEGRATION_GROUP_ID = "org.springframework.integration";
    private static final String SPRING_INTEGRATION_ARTIFACT_ID = "spring-integration-core";

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

    private static boolean isApplicableMavenProject(MavenResolutionResult model) {
        if (model.findDependencies(SPRING_INTEGRATION_GROUP_ID, SPRING_INTEGRATION_ARTIFACT_ID, Scope.Compile).isEmpty()) {
            return false;
        }
        for (ResolvedDependency d : model.getDependencies().getOrDefault(Scope.Compile, emptyList())) {
            if (SPRING_BOOT_24.matches(d.getGroupId(), d.getArtifactId(), d.getVersion())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isApplicableGradleProject(GradleProject gradleProject) {
        GradleDependencyConfiguration configuration = gradleProject.getConfiguration("compileClasspath");
        if (configuration == null ||
                configuration.findResolvedDependency(SPRING_INTEGRATION_GROUP_ID, SPRING_INTEGRATION_ARTIFACT_ID) == null) {
            return false;
        }
        ResolvedDependency boot = configuration.findResolvedDependency("org.springframework.boot", "spring-boot");
        return boot != null && SPRING_BOOT_24.matches(boot.getGroupId(), boot.getArtifactId(), boot.getVersion());
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

                if (!acc.getApplicableProjects().contains(javaProject)) {
                    Optional<MavenResolutionResult> maven = source.getMarkers().findFirst(MavenResolutionResult.class);
                    if (maven.isPresent()) {
                        if (isApplicableMavenProject(maven.get())) {
                            acc.getApplicableProjects().add(javaProject);
                        }
                    } else if (source.getMarkers().findFirst(GradleProject.class)
                            .map(FindIntegrationSchedulerPool::isApplicableGradleProject).orElse(false)) {
                        acc.getApplicableProjects().add(javaProject);
                    }
                }

                if (source instanceof Properties && APP_PROPS_FILE_REGEX.matcher(fileName).matches()) {
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

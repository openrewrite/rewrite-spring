/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.boot2.search;

import lombok.Value;
import lombok.With;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.semver.DependencyMatcher;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Alex Boyko
 */
public class IntegrationSchedulerPoolRecipe extends Recipe {

    private static final Pattern APP_PROPS_FILE_REGEX = Pattern.compile("^application.*\\.properties$");
    private static final Pattern APP_YAML_FILE_REGEX = Pattern.compile("^application.*\\.ya?ml$");

    private static final String PROPERTY_KEY = "spring.task.scheduling.pool.size";

    private static final String PROPS_MIGRATION_MESSAGE = " TODO: Consider Scheduler thread pool size for Spring Integration";
    private static final String GENERAL_MIGRATION_MESSAGE = " TODO: Scheduler thread pool size for Spring Integration either in properties or config server\n";

    @Override
    public String getDisplayName() {
        return "Integration scheduler pool size";
    }

    @Override
    public String getDescription() {
        return "Spring Integration now reuses an available TaskScheduler rather than configuring its own. In a" +
                " typical application setup relying on the auto-configuration, this means that Spring Integration" +
                " uses the auto-configured task scheduler that has a pool size of 1. To restore Spring Integration’s" +
                " default of 10 threads, use the `spring.task.scheduling.pool.size` property.";
    }

    private boolean isApplicableMavenProject(Xml.Document maven) {
        DependencyMatcher boot25Matcher = DependencyMatcher.build("org.springframework.boot:spring-boot:2.4.X").getValue();
        DependencyMatcher integrationMatcher = DependencyMatcher.build("org.springframework.integration:spring-integration-core").getValue();

        List<ResolvedDependency> deps = maven.getMarkers().findFirst(MavenResolutionResult.class)
                .orElseThrow(() -> new IllegalStateException("Maven visitors should not be visiting XML documents without a Maven marker"))
                .getDependencies().getOrDefault(Scope.Compile, Collections.emptyList());

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

    @Override
    protected MavenVisitor<ExecutionContext> getApplicableTest() {
        return new MavenVisitor<ExecutionContext>() {

            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext ctx) {

                if (isApplicableMavenProject(document)) {
                    return SearchResult.found(document);
                }
                return document;
            }
        };
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        Set<JavaProject> javaProjects = before.stream()
                .filter(s -> s.getMarkers().findFirst(MavenResolutionResult.class).isPresent())
                .map(Xml.Document.class::cast)
                .filter(this::isApplicableMavenProject)
                .map(m -> m.getMarkers().findFirst(JavaProject.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        // Leave the comment about scheduler pool size next to 'spring.task.scheduling.pool.size' property in the external properties file (props or yaml)
        List<SourceFile> modified = ListUtils.map(before, source -> {
            if (source.getMarkers().findFirst(CommentAdded.class).isPresent()) {
                JavaProject javaProject = source.getMarkers().findFirst(JavaProject.class).orElse(null);
                javaProjects.remove(javaProject);
                return source;
            }
            String fileName = source.getSourcePath().getFileName().toString();
            JavaProject javaProject = source.getMarkers().findFirst(JavaProject.class).orElse(null);
            if (javaProjects.contains(javaProject)) {
                if (APP_PROPS_FILE_REGEX.matcher(fileName).matches() && source instanceof Properties) {
                    Set<Properties.Entry> foundEntries = FindProperties.find((Properties) source, PROPERTY_KEY, false);
                    if (!foundEntries.isEmpty()) {
                        // There should only be one exact match!
                        Properties.Entry entry = foundEntries.iterator().next();
                        source = (SourceFile) new PropertiesVisitor<ExecutionContext>() {
                            @Override
                            public Properties visitFile(Properties.File file, ExecutionContext context) {
                                int idx = file.getContent().indexOf(entry);
                                if (idx >= 0) {
                                    Properties.Comment comment = new Properties.Comment(Tree.randomId(), "\n", Markers.EMPTY, Properties.Comment.Delimiter.HASH_TAG, PROPS_MIGRATION_MESSAGE);
                                    return file.withContent(ListUtils.insertAll(file.getContent(), idx, Collections.singletonList(comment)));
                                } else {
                                    throw new RuntimeException("Entry must be present in the properties file!");
                                }
                            }
                        }.visitNonNull(source, ctx);
                        javaProjects.remove(javaProject);
                        source = source.withMarkers(source.getMarkers().addIfAbsent(new CommentAdded(Tree.randomId())));
                    }
                } else if (APP_YAML_FILE_REGEX.matcher(fileName).matches() && source instanceof Yaml) {
                    Set<Yaml.Block> foundEntriesValues = FindProperty.find((Yaml) source, PROPERTY_KEY, false);
                    if (!foundEntriesValues.isEmpty()) {
                        source = (SourceFile) new YamlIsoVisitor<ExecutionContext>(){
                            @Override
                            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext context) {
                                if (foundEntriesValues.contains(entry.getValue())) {
                                    entry = entry.withPrefix("\n#" + PROPS_MIGRATION_MESSAGE + entry.getPrefix());
                                }
                                return super.visitMappingEntry(entry, context);
                            }
                        }.visitNonNull(source, ctx);
                        javaProjects.remove(javaProject);
                        source = source.withMarkers(source.getMarkers().addIfAbsent(new CommentAdded(Tree.randomId())));
                    }
                }
            }
            return source;
        });

        if (!javaProjects.isEmpty()) {
            AnnotationMatcher annotationMatcher = new AnnotationMatcher("@org.springframework.boot.autoconfigure.SpringBootApplication");
            JavaIsoVisitor<ExecutionContext> commentVisitor = new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext context) {
                    J.Annotation a = super.visitAnnotation(annotation, context);
                    if (annotationMatcher.matches(a)) {
                        a = a.withComments(ListUtils.concat(a.getComments(), new TextComment(false, GENERAL_MIGRATION_MESSAGE, "", Markers.EMPTY)));
                    }
                    return a;
                }
            };
            modified = ListUtils.map(modified, source -> {
                if (source.getMarkers().findFirst(CommentAdded.class).isPresent()) {
                    return source;
                }
                JavaProject javaProject = source.getMarkers().findFirst(JavaProject.class).orElse(null);
                if (javaProjects.contains(javaProject) && source instanceof J.CompilationUnit) {
                    SourceFile after = (SourceFile) commentVisitor.visitNonNull(source, ctx);
                    if (after != source) {
                        javaProjects.remove(javaProject);
                        after = after.withMarkers(after.getMarkers().addIfAbsent(new CommentAdded(Tree.randomId())));
                        return after;
                    }
                }
                return source;
            });
        }
        return modified;
    }

    @Value
    @With
    private static class CommentAdded implements Marker {
        UUID id;
    }
}

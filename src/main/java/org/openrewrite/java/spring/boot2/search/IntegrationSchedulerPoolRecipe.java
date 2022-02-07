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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;
import org.openrewrite.maven.tree.Pom;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.semver.DependencyMatcher;
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

    private static Pattern APP_PROPS_FILE_REGEX = Pattern.compile("^application.*\\.properties$");
    private static Pattern APP_YAML_FILE_REGEX = Pattern.compile("^application.*\\.ya?ml$");

    private static final String PROPERTY_KEY = "spring.task.scheduling.pool.size";

    private static final String PROPS_MIGRATION_MESSAGE = " TODO: Consider Scheduler thread pool size for Spring Integration";
    private static final String GENERAL_MIGRATION_MESSAGE = " TODO: Scheduler thread pool size for Spring Integration either in properties or config server\n";

    @Override
    public String getDisplayName() {
        return "Integration Sceduler Pool Size";
    }

    @Override
    public String getDescription() {
        return "Spring Integration now reuses an available TaskScheduler rather than configuring its own. In a" +
                " typical application setup relying on the auto-configuration, this means that Spring Integration" +
                " uses the auto-configured task scheduler that has a pool size of 1. To restore Spring Integration’s" +
                " default of 10 threads, use the spring.task.scheduling.pool.size property.";
    }

    private boolean isApplicableMavenProject(Maven maven) {
        DependencyMatcher boot25Matcher = DependencyMatcher.build("org.springframework.boot:spring-boot:2.4.X").getValue();
        DependencyMatcher integrationMatcher = DependencyMatcher.build("org.springframework.integration:spring-integration-core").getValue();
        Collection<Pom.Dependency> deps = maven.getModel().getDependencies(Scope.Compile);
        boolean boot25 = false;
        boolean si = false;
        for (Pom.Dependency d : deps) {
            if (!boot25) {
                boot25 = boot25Matcher.matches(d.getGroupId(), d.getArtifactId(), d.getVersion());
            }
            if (!si) {
                si = integrationMatcher.matches(d.getGroupId(), d.getArtifactId());
            }
            if (boot25 && si) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected MavenVisitor getApplicableTest() {
        return new MavenVisitor() {
            @Override
            public Maven visitMaven(Maven maven, ExecutionContext ctx) {
                if (isApplicableMavenProject(maven)) {
                  return maven.withMarkers(maven.getMarkers().searchResult());
                }
                return maven;
            }
        };
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        Set<JavaProject> javaProjects = before.stream()
                .filter(Maven.class::isInstance)
                .map(Maven.class::cast)
                .filter(this::isApplicableMavenProject)
                .map(m -> m.getMarkers().findFirst(JavaProject.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        Set<JavaProject> projectsWithCommentOverProperty = new HashSet<>();

        // Leave the comment about scheduler pool size next to 'spring.task.scheduling.pool.size' property in the external properties file (props or yaml)
        List<SourceFile> modified = ListUtils.map(before, source -> {
            String fileName = source.getSourcePath().getFileName().toString();
            JavaProject javaProject = source.getMarkers().findFirst(JavaProject.class).orElse(null);
            if (javaProjects.contains(javaProject)) {
                if (APP_PROPS_FILE_REGEX.matcher(fileName).matches() && source instanceof Properties) {
                    Set<Properties.Entry> foundEntries = FindProperties.find((Properties) source, PROPERTY_KEY, false);
                    if (!foundEntries.isEmpty()) {
                        projectsWithCommentOverProperty.add(javaProject);
                        // There should only be one exact match!
                        Properties.Entry entry = foundEntries.iterator().next();
                        return (SourceFile) new PropertiesVisitor<ExecutionContext>() {
                            @Override
                            public Properties visitFile(Properties.File file, ExecutionContext context) {
                                int idx = file.getContent().indexOf(entry);
                                if (idx >= 0) {
                                    Properties.Comment comment = new Properties.Comment(Tree.randomId(), "\n", Markers.EMPTY, PROPS_MIGRATION_MESSAGE);
                                    List<Properties.Content> contents = new ArrayList<>();
                                    contents.add(idx, comment);
                                    return file.withContent(contents);
                                } else {
                                    throw new RuntimeException("Entry must be present in the properties file!");
                                }
                            }
                        }.visitNonNull(source, ctx);
                    }
                } else if (APP_YAML_FILE_REGEX.matcher(fileName).matches() && source instanceof Yaml) {
                    Set<Yaml.Block> foundEntriesValues = FindProperty.find((Yaml) source, PROPERTY_KEY, false);
                    if (!foundEntriesValues.isEmpty()) {
                        projectsWithCommentOverProperty.add(javaProject);
                        return (SourceFile) new YamlIsoVisitor<ExecutionContext>(){
                            @Override
                            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext context) {
                                if (foundEntriesValues.contains(entry.getValue())) {
                                    return entry.withPrefix("\n#" + PROPS_MIGRATION_MESSAGE + entry.getPrefix());
                                }
                                return super.visitMappingEntry(entry, context);
                            }
                        }.visitNonNull(source, ctx);
                    }
                }
            }
            return source;
        });

        javaProjects.removeAll(projectsWithCommentOverProperty);

        // Leave generic comment next Boot Application main class declaration since no property value specified for thread pool size
        modified = ListUtils.map(modified, source -> {
            if (javaProjects.contains(source.getMarkers().findFirst(JavaProject.class).orElse(null))) {
                AnnotationMatcher annotationMatcher = new AnnotationMatcher("@org.springframework.boot.autoconfigure.SpringBootApplication");
                return (SourceFile) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext context) {
                        J.Annotation a = super.visitAnnotation(annotation, context);
                        if (annotationMatcher.matches(a)) {
                            a = a.withComments(ListUtils.concat(a.getComments(), new TextComment(false, GENERAL_MIGRATION_MESSAGE, "", Markers.EMPTY)));
                        }
                        return a;
                    }
                }.visitNonNull(source, ctx);
            }
            return source;
        });

        return modified;
    }

}

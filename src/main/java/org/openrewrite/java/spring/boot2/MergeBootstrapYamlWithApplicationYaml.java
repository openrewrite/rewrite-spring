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
package org.openrewrite.java.spring.boot2;

import lombok.Data;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.spring.ExpandProperties;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.yaml.CoalescePropertiesVisitor;
import org.openrewrite.yaml.MergeYamlVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class MergeBootstrapYamlWithApplicationYaml extends ScanningRecipe<MergeBootstrapYamlWithApplicationYaml.Accumulator> {

    @Getter
    final String displayName = "Merge Spring `bootstrap.yml` with `application.yml`";

    @Getter
    final String description = "In Spring Boot 2.4, the bootstrap context that loads `bootstrap.yml` is " +
            "[disabled by default](https://docs.spring.io/spring-cloud-config/reference/client.html). " +
            "Its properties should be merged with `application.yml` unless `spring-cloud-starter-bootstrap` is present as a dependency.";

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile source = (SourceFile) tree;
                Path sourcePath = source.getSourcePath();
                if (acc.getBootstrapYaml() == null && PathUtils.matchesGlob(sourcePath, "**/main/resources/bootstrap.y*ml")) {
                    acc.setBootstrapYaml(source);
                } else if (acc.getApplicationYaml() == null && PathUtils.matchesGlob(sourcePath, "**/main/resources/application.y*ml")) {
                    acc.setApplicationYaml(source);
                }
                if (!acc.isSpringCloudBootstrapPresent()) {
                    source.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(maven -> {
                        List<ResolvedDependency> deps = maven.getDependencies().getOrDefault(Scope.Compile, emptyList());
                        for (ResolvedDependency d : deps) {
                            if (isSpringCloudBootstrap(d)) {
                                acc.setSpringCloudBootstrapPresent(true);
                                break;
                            }
                        }
                    });
                    source.getMarkers().findFirst(GradleProject.class).ifPresent(gradle -> {
                        GradleDependencyConfiguration compileClasspath = gradle.getConfiguration("compileClasspath");
                        if (compileClasspath != null) {
                            for (ResolvedDependency d : compileClasspath.getResolved()) {
                                if (isSpringCloudBootstrap(d)) {
                                    acc.setSpringCloudBootstrapPresent(true);
                                    break;
                                }
                            }
                        }
                    });
                }
                return source;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (acc.isSpringCloudBootstrapPresent()) {
            return emptyList();
        }
        if (acc.getBootstrapYaml() instanceof Yaml.Documents && acc.getApplicationYaml() == null) {
            // rename
            Yaml.Documents yaml = (Yaml.Documents) acc.getBootstrapYaml();
            String fileName = PathUtils.matchesGlob(yaml.getSourcePath(), "**/*.yaml") ? "application.yaml" : "application.yml";
            Optional<SourceFile> newApplicationYaml = YamlParser.builder().build()
                                .parse("")
                                .map(brandNewFile -> (SourceFile) brandNewFile
                                        .withSourcePath(yaml.getSourcePath().resolveSibling(fileName)))
                    .findFirst();
            if (newApplicationYaml.isPresent()) {
                acc.applicationYaml = newApplicationYaml.get();
                return singletonList(newApplicationYaml.get());
            }
        }
        return emptyList();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.isSpringCloudBootstrapPresent() ||
            !(acc.getBootstrapYaml() instanceof Yaml.Documents && acc.getApplicationYaml() instanceof Yaml.Documents)) {
            return TreeVisitor.noop();
        }

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile source = (SourceFile) tree;
                Path sourcePath = source.getSourcePath();
                if (sourcePath.equals(acc.getBootstrapYaml().getSourcePath())) {
                    // delete bootstrap.yml file
                    source = null;
                } else if (acc.getApplicationYaml() != null && sourcePath.equals(acc.getApplicationYaml().getSourcePath())) {
                    // update application.yml file
                    AtomicBoolean merged = new AtomicBoolean(false);

                    Yaml.Documents a = (Yaml.Documents) new ExpandProperties(null).getVisitor().visit(acc.getApplicationYaml(), ctx);
                    Yaml.Documents b = (Yaml.Documents) new ExpandProperties(null).getVisitor().visit(acc.getBootstrapYaml(), ctx);
                    assert a != null;
                    assert b != null;

                    //noinspection unchecked
                    source = new CoalescePropertiesVisitor<Integer>(null, null).visitDocuments(a.withDocuments(ListUtils.map(a.getDocuments(), doc -> {
                        if (doc == null) {
                            return null;
                        }
                        if (merged.compareAndSet(false, true) && FindProperty.find(doc, "spring.config.activate.on-profile", true).isEmpty()) {
                            Yaml.Document mergedDocument = doc;
                            Yaml.Document mergedDocumentOrNull;
                            for (Yaml.Document d : b.getDocuments()) {
                                if (FindProperty.find(d, "spring.config.activate.on-profile", true).isEmpty()) {
                                    mergedDocumentOrNull = (Yaml.Document) new MergeYamlVisitor<Integer>(mergedDocument.getBlock(), d.getBlock(), true, null, null, null).visit(mergedDocument, 0, new Cursor(new Cursor(null, a), mergedDocument));
                                    if (mergedDocumentOrNull != null) {
                                        mergedDocument = mergedDocumentOrNull;
                                    }
                                }
                            }
                            return mergedDocument;
                        }
                        return doc;
                    })), 0);
                }
                return source;
            }
        };
    }

    private static boolean isSpringCloudBootstrap(ResolvedDependency d) {
        return "org.springframework.cloud".equals(d.getGroupId()) &&
               "spring-cloud-starter-bootstrap".equals(d.getArtifactId());
    }

    @Data
    static class Accumulator {
        @Nullable SourceFile bootstrapYaml;

        @Nullable SourceFile applicationYaml;

        boolean springCloudBootstrapPresent;
    }
}

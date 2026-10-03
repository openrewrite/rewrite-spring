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
package org.openrewrite.java.spring.boot4;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.search.FindPlugins;
import org.openrewrite.java.dependencies.DependencyInsight;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Pom;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Restricts the composite migration to source files belonging to a Boot build. */
public class UsesSpringBoot extends ScanningRecipe<Map<Path, Boolean>> {
    @Getter
    final String displayName = "Find Spring Boot projects";

    @Getter
    final String description = "Find build files using Spring Boot and their source files, without treating dependencies merely managed by an unrelated BOM as usage.";

    @Override
    public Map<Path, Boolean> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<Path, Boolean> builds) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> dependencies = new DependencyInsight("org.springframework.boot", "*", null, null).getVisitor();
            final TreeVisitor<?, ExecutionContext> plugins = new FindPlugins("org.springframework.boot", null).getVisitor();

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile source = (SourceFile) tree;
                String file = source.getSourcePath().getFileName().toString();
                if (!"pom.xml".equals(file) && !"build.gradle".equals(file) && !"build.gradle.kts".equals(file)) {
                    return tree;
                }
                boolean boot = dependencies.visit(tree, ctx) != tree;
                if (!boot && !"pom.xml".equals(file)) {
                    boot = plugins.visit(tree, ctx) != tree;
                }
                if (!boot) {
                    MavenResolutionResult resolution = source.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
                    if (resolution != null) {
                        Pom pom = resolution.getPom().getRequested();
                        boot = pom.getParent() != null && "org.springframework.boot".equals(pom.getParent().getGroupId()) ||
                               pom.getDependencyManagement().stream().anyMatch(dependency ->
                                       "org.springframework.boot".equals(dependency.getGroupId()) &&
                                       "spring-boot-dependencies".equals(dependency.getArtifactId()));
                    }
                }
                Path parent = source.getSourcePath().getParent();
                builds.put(parent == null ? source.getSourcePath().getFileSystem().getPath("") : parent, boot);
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<Path, Boolean> builds) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                // Without build metadata, preserve source-only uses of the migration.
                if (builds.isEmpty()) {
                    return SearchResult.found(tree);
                }
                Path path = ((SourceFile) tree).getSourcePath();
                Path directory = path.getParent();
                while (directory != null) {
                    Boolean boot = builds.get(directory);
                    if (boot != null) {
                        return boot ? SearchResult.found(tree) : tree;
                    }
                    directory = directory.getParent();
                }
                return Boolean.TRUE.equals(builds.get(path.getFileSystem().getPath(""))) ? SearchResult.found(tree) : tree;
            }
        };
    }
}

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

/**
 * Restricts the composite migration to repositories with a Spring Boot build. The decision is made for the
 * repository as a whole, so modules that do not depend on Spring Boot themselves, such as libraries built under a
 * company parent that extends Boot's, migrate along with the application modules that do.
 */
public class UsesSpringBoot extends ScanningRecipe<UsesSpringBoot.Accumulator> {
    @Getter
    final String displayName = "Find Spring Boot repositories";

    @Getter
    final String description = "Find every source file of a repository in which some build uses Spring Boot: a Spring Boot " +
                               "dependency, direct or transitive, the Spring Boot Gradle plugin, a Spring Boot parent POM, " +
                               "or an imported `spring-boot-dependencies` BOM. A repository without build files matches.";

    public static class Accumulator {
        boolean build;
        boolean boot;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
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
                acc.build = true;
                if (!acc.boot) {
                    acc.boot = dependencies.visit(tree, ctx) != tree ||
                               !"pom.xml".equals(file) && plugins.visit(tree, ctx) != tree ||
                               bootParentOrBom(source);
                }
                return tree;
            }

            private boolean bootParentOrBom(SourceFile source) {
                MavenResolutionResult resolution = source.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
                if (resolution == null) {
                    return false;
                }
                Pom pom = resolution.getPom().getRequested();
                return pom.getParent() != null && "org.springframework.boot".equals(pom.getParent().getGroupId()) ||
                       pom.getDependencyManagement().stream().anyMatch(dependency ->
                               "org.springframework.boot".equals(dependency.getGroupId()) &&
                               "spring-boot-dependencies".equals(dependency.getArtifactId()));
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                // Without build metadata, preserve source-only uses of the migration.
                if (tree instanceof SourceFile && (!acc.build || acc.boot)) {
                    return SearchResult.found(tree);
                }
                return tree;
            }
        };
    }
}

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
package org.openrewrite.java.spring.cloud2022;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Scope;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class AddLoggingPatternLevelForSleuth extends ScanningRecipe<Set<JavaProject>> {

    private static final String SLEUTH_GROUP_ID = "org.springframework.cloud";
    private static final String SLEUTH_ARTIFACT_ID = "spring-cloud-starter-sleuth";

    @Getter
    final String displayName = "Add `logging.pattern.level` for `traceId` and `spanId`";

    @Getter
    final String description = "Add `logging.pattern.level` for `traceId` and `spanId` which was previously set by default, if not already set.";

    @Override
    public Set<JavaProject> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                Optional<JavaProject> maybeJavaProject = tree.getMarkers().findFirst(JavaProject.class);
                if (!maybeJavaProject.isPresent() || acc.contains(maybeJavaProject.get())) {
                    return tree;
                }
                Optional<MavenResolutionResult> maven = tree.getMarkers().findFirst(MavenResolutionResult.class);
                if (maven.isPresent()) {
                    if (!maven.get().findDependencies(SLEUTH_GROUP_ID, SLEUTH_ARTIFACT_ID, Scope.Compile).isEmpty()) {
                        acc.add(maybeJavaProject.get());
                    }
                    return tree;
                }
                Optional<GradleProject> gradle = tree.getMarkers().findFirst(GradleProject.class);
                if (gradle.isPresent()) {
                    GradleDependencyConfiguration configuration = gradle.get().getConfiguration("compileClasspath");
                    if (configuration != null && configuration.findResolvedDependency(SLEUTH_GROUP_ID, SLEUTH_ARTIFACT_ID) != null) {
                        acc.add(maybeJavaProject.get());
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> acc) {
        AddSpringProperty addSpringProperty = new AddSpringProperty(
                "logging.pattern.level",
                "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]",
                "Logging pattern containing traceId and spanId; no longer provided through Sleuth by default",
                null);
        return Preconditions.check(new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    Optional<JavaProject> javaProject = tree.getMarkers().findFirst(JavaProject.class);
                    if (javaProject.isPresent() && acc.contains(javaProject.get())) {
                        return SearchResult.found(tree);
                    }
                }
                return tree;
            }
        }, addSpringProperty.getVisitor());
    }
}

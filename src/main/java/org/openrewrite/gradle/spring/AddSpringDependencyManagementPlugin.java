/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.gradle.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.gradle.plugins.AddBuildPlugin;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddSpringDependencyManagementPlugin extends Recipe {
    @Override
    public String getDisplayName() {
        return "Add `io.spring.dependency-management` plugin, if in use";
    }

    @Override
    public String getDescription() {
        return "Add `io.spring.dependency-management` plugin, if in use.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new IsBuildGradle<>(),
                        new UsesSpringDependencyManagement()
                ),
                new AddBuildPlugin("io.spring.dependency-management", "1.0.6.RELEASE", null, null).getVisitor()
        );
    }

    private static class UsesSpringDependencyManagement extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile cu = (JavaSourceFile) tree;
                Optional<GradleProject> maybeGp = cu.getMarkers().findFirst(GradleProject.class);
                if (!maybeGp.isPresent()) {
                    return cu;
                }
                GradleProject gp = maybeGp.get();
                if (gp.getPlugins().stream().anyMatch(plugin -> "io.spring.dependency-management".equals(plugin.getId()) ||
                        "io.spring.gradle.dependencymanagement.DependencyManagementPlugin".equals(plugin.getFullyQualifiedClassName()))) {
                    return SearchResult.found(cu);
                }
            }
            return super.visit(tree, ctx);
        }
    }
}

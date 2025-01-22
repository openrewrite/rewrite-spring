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
package org.openrewrite.gradle.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.gradle.plugins.AddBuildPlugin;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddSpringDependencyManagementPlugin extends Recipe {
    @Override
    public String getDisplayName() {
        return "Add `io.spring.dependency-management` plugin, if in use";
    }

    @Override
    public String getDescription() {
        return "Prior to Spring Boot 2.0 the dependency management plugin was applied automatically as part of the overall spring boot plugin. " +
               "Afterwards the dependency-management plugin must be applied explicitly, or Gradle's `platform()` feature may be used instead. " +
               "This recipe makes usage of io-spring.dependency-management explicit in anticipation of upgrade to Spring Boot 2.0 or later.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new IsBuildGradle<>(),
                        new UsesSpringDependencyManagement()
                ),
                new AddBuildPlugin("io.spring.dependency-management", "1.0.6.RELEASE", null, null, false).getVisitor()
        );
    }

    private static class UsesSpringDependencyManagement extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile cu = (JavaSourceFile) tree;
                Optional<GradleProject> maybeGp = cu.getMarkers().findFirst(GradleProject.class);
                if (!maybeGp.isPresent()) {
                    return cu;
                }
                GradleProject gp = maybeGp.get();
                if (gp.getPlugins().stream().anyMatch(plugin -> "io.spring.dependency-management".equals(plugin.getId()) ||
                        "io.spring.gradle.dependencymanagement.DependencyManagementPlugin".equals(plugin.getFullyQualifiedClassName()))
                    && usesDependencyManagementDsl(cu)
                ) {
                    return SearchResult.found(cu);
                }
            }
            return super.visit(tree, ctx);
        }
    }

    private static boolean usesDependencyManagementDsl(JavaSourceFile cu) {
        AtomicBoolean found = new AtomicBoolean(false);
        new UsesDependencyManagementDslVisitor().visit(cu, found);
        return found.get();
    }

    private static class UsesDependencyManagementDslVisitor extends GroovyIsoVisitor<AtomicBoolean> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
            if ("dependencyManagement".equals(method.getSimpleName())) {
                found.set(true);
                return method;
            }
            return super.visitMethodInvocation(method, found);
        }

        @Override
        public @Nullable J visit(@Nullable Tree tree, AtomicBoolean found) {
            if (found.get()) {
                return (J) tree;
            }
            return super.visit(tree, found);
        }
    }
}

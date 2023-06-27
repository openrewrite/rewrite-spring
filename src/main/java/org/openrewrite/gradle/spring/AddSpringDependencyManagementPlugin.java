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
@EqualsAndHashCode(callSuper = true)
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
                new AddBuildPlugin("io.spring.dependency-management", "1.0.6", ".RELEASE").getVisitor()
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

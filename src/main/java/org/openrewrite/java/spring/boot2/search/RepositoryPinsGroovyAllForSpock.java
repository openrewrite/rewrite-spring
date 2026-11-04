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
package org.openrewrite.java.spring.boot2.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.gradle.trait.GradleMultiDependency;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.ManagedDependency;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Pom;

@EqualsAndHashCode(callSuper = false)
@Value
public class RepositoryPinsGroovyAllForSpock extends ScanningRecipe<RepositoryPinsGroovyAllForSpock.Accumulator> {

    String displayName = "Repository pins `groovy-all` for Spock 1.x";

    String description = "Marks every source file of a repository that declares both `groovy-all` below Groovy 3 and " +
            "Spock 1.x in a way `UpgradeDependencyVersion` can change, so that the two can move together. A version " +
            "the repository inherits, keeps in a map or declares for a configuration its project does not have stays " +
            "where it is, and upgrading only the other one would leave Groovy and Spock apart. Every source file is " +
            "marked because such a pin can live in a parent POM or a `gradle.properties` that no dependency search " +
            "would match. This recipe is intended to be used as a precondition for other recipes.";

    public static class Accumulator {
        boolean pinsGroovyAll;
        boolean pinsSpock1;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree != null) {
                    tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> scan(mrr, acc));
                    if (tree instanceof JavaSourceFile) {
                        tree.getMarkers().findFirst(GradleProject.class).ifPresent(project ->
                                scan((JavaSourceFile) tree, project, acc, ctx));
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return Preconditions.check(acc.pinsGroovyAll && acc.pinsSpock1, new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return tree == null ? null : SearchResult.found(tree);
            }
        });
    }

    private static void scan(MavenResolutionResult mrr, Accumulator acc) {
        Pom requested = mrr.getPom().getRequested();
        for (Dependency dependency : requested.getDependencies()) {
            scan(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), mrr, acc);
        }
        for (ManagedDependency managed : requested.getDependencyManagement()) {
            scan(managed.getGroupId(), managed.getArtifactId(), managed.getVersion(), mrr, acc);
        }
    }

    private static void scan(@Nullable String groupId, String artifactId, @Nullable String version,
                             MavenResolutionResult mrr, Accumulator acc) {
        if (version != null && !(version.startsWith("${") && version.endsWith("}") &&
                                 !declaresProperty(version.substring(2, version.length() - 1), mrr))) {
            record(groupId, artifactId, mrr.getPom().getValue(version), acc);
        }
    }

    /**
     * {@link MavenResolutionResult#getParent()} only reaches poms that are sources of this repository,
     * so a property of an external parent such as {@code spring-boot-starter-parent} is not found.
     */
    private static boolean declaresProperty(String property, MavenResolutionResult mrr) {
        for (MavenResolutionResult current = mrr; current != null; current = current.getParent()) {
            if (current.getPom().getRequested().getProperties().containsKey(property)) {
                return true;
            }
        }
        return false;
    }

    private static void scan(JavaSourceFile buildScript, GradleProject project, Accumulator acc, ExecutionContext ctx) {
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext c) {
                GradleMultiDependency.matcher().get(getCursor()).ifPresent(declared -> declared.forEach(dependency -> {
                    String variable = dependency.getVersionVariable();
                    if (project.getConfiguration(dependency.getConfigurationName()) != null &&
                        (variable == null || !variable.contains("."))) {
                        record(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), acc);
                    }
                }));
                return super.visitMethodInvocation(method, c);
            }
        }.visit(buildScript, ctx);
    }

    private static void record(@Nullable String groupId, String artifactId, @Nullable String version, Accumulator acc) {
        if (version == null) {
            return;
        }
        if ("org.codehaus.groovy".equals(groupId) && "groovy-all".equals(artifactId)) {
            acc.pinsGroovyAll |= version.startsWith("1.") || version.startsWith("2.");
        } else if ("org.spockframework".equals(groupId)) {
            acc.pinsSpock1 |= version.startsWith("0.") || version.startsWith("1.");
        }
    }
}

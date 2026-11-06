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
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.gradle.trait.GradleMultiDependency;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.ManagedDependency;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Pom;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class RepositoryPinsDependency extends ScanningRecipe<AtomicBoolean> {
    private static final Pattern MAJOR_VERSION = Pattern.compile("^(\\d+)\\.");

    @Option(displayName = "Group ID",
            description = "The first part of a dependency coordinate `org.spockframework:spock-core:VERSION`.",
            example = "org.spockframework")
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The second part of a dependency coordinate `org.spockframework:spock-core:VERSION`. " +
                          "This can be a glob expression.",
            example = "spock-*")
    String artifactId;

    @Option(displayName = "Below major version",
            description = "Only a version with a lower major version counts.",
            example = "2")
    int belowMajorVersion;

    String displayName = "Repository pins a dependency below a major version";

    String description = "Marks every source file of a repository that declares a dependency below a major version " +
            "in a way `UpgradeDependencyVersion` can change. A version the repository inherits, keeps in a map or " +
            "declares for a configuration its project does not have does not count, since it would stay where it " +
            "is. Every source file is marked because such a pin can live in a parent POM or a `gradle.properties` " +
            "that no dependency search would match. This recipe is intended to be used as a precondition for other " +
            "recipes.";

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean pinned) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree != null && !pinned.get()) {
                    tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> scan(mrr, pinned));
                    if (tree instanceof JavaSourceFile) {
                        tree.getMarkers().findFirst(GradleProject.class).ifPresent(project ->
                                scan((JavaSourceFile) tree, project, pinned, ctx));
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean pinned) {
        return Preconditions.check(pinned.get(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return tree == null ? null : SearchResult.found(tree);
            }
        });
    }

    private void scan(MavenResolutionResult mrr, AtomicBoolean pinned) {
        Pom requested = mrr.getPom().getRequested();
        for (Dependency dependency : requested.getDependencies()) {
            scan(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), mrr, pinned);
        }
        for (ManagedDependency managed : requested.getDependencyManagement()) {
            scan(managed.getGroupId(), managed.getArtifactId(), managed.getVersion(), mrr, pinned);
        }
    }

    private void scan(@Nullable String group, String artifact, @Nullable String version,
                      MavenResolutionResult mrr, AtomicBoolean pinned) {
        if (version != null && !(version.startsWith("${") && version.endsWith("}") &&
                                 !declaresProperty(version.substring(2, version.length() - 1), mrr))) {
            record(group, artifact, mrr.getPom().getValue(version), pinned);
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

    private void scan(JavaSourceFile buildScript, GradleProject project, AtomicBoolean pinned, ExecutionContext ctx) {
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext c) {
                GradleMultiDependency.matcher().get(getCursor()).ifPresent(declared -> declared.forEach(dependency -> {
                    String variable = dependency.getVersionVariable();
                    if (project.getConfiguration(dependency.getConfigurationName()) != null &&
                        (variable == null || !variable.contains("."))) {
                        record(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), pinned);
                    }
                }));
                return super.visitMethodInvocation(method, c);
            }
        }.visit(buildScript, ctx);
    }

    private void record(@Nullable String group, String artifact, @Nullable String version, AtomicBoolean pinned) {
        if (version != null && groupId.equals(group) && StringUtils.matchesGlob(artifact, artifactId)) {
            Matcher major = MAJOR_VERSION.matcher(version);
            if (major.find() && Integer.parseInt(major.group(1)) < belowMajorVersion) {
                pinned.set(true);
            }
        }
    }
}

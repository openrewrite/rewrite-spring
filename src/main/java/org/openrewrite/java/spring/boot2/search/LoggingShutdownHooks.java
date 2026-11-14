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
package org.openrewrite.java.spring.boot2.search;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradlePluginDescriptor;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.DependencyMatcher;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public class LoggingShutdownHooks extends ScanningRecipe<Set<JavaProject>> {

    private static final DependencyMatcher SPRING_BOOT_24 = requireNonNull(
            DependencyMatcher.build("org.springframework.boot:spring-boot:2.4.X").getValue());

    @Getter
    final String displayName = "Applications using logging shutdown hooks";

    @Getter
    final String description = "Spring Boot registers a logging shutdown hook by default for JAR-based applications to ensure that " +
            "logging resources are released when the JVM exits. If your application is deployed as a WAR then " +
            "the shutdown hook is not registered since the servlet container usually handles logging concerns. " +
            "\n\n" +
            "Most applications will want the shutdown hook. However, if your application has complex context " +
            "hierarchies, then you may need to disable it. You can use the `logging.register-shutdown-hook` " +
            "property to do that.";

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
                    if (isApplicableMavenProject(maven.get())) {
                        acc.add(maybeJavaProject.get());
                    }
                    return tree;
                }
                Optional<GradleProject> gradle = tree.getMarkers().findFirst(GradleProject.class);
                if (gradle.isPresent() && isApplicableGradleProject(gradle.get())) {
                    acc.add(maybeJavaProject.get());
                }
                return tree;
            }
        };
    }

    private static boolean isApplicableMavenProject(MavenResolutionResult model) {
        //Default packaging, if not specified is "jar"
        if (!"jar".equals(model.getPom().getPackaging())) {
            return false;
        }
        for (ResolvedDependency d : model.getDependencies().getOrDefault(Scope.Compile, emptyList())) {
            if (SPRING_BOOT_24.matches(d.getGroupId(), d.getArtifactId(), d.getVersion())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isApplicableGradleProject(GradleProject gradleProject) {
        for (GradlePluginDescriptor plugin : gradleProject.getPlugins()) {
            if ("war".equals(plugin.getId()) ||
                "org.gradle.war".equals(plugin.getId()) ||
                plugin.getFullyQualifiedClassName().startsWith("org.gradle.api.plugins.WarPlugin")) {
                return false;
            }
        }
        GradleDependencyConfiguration configuration = gradleProject.getConfiguration("compileClasspath");
        if (configuration == null) {
            return false;
        }
        ResolvedDependency d = configuration.findResolvedDependency("org.springframework.boot", "spring-boot");
        return d != null && SPRING_BOOT_24.matches(d.getGroupId(), d.getArtifactId(), d.getVersion());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> acc) {
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
        }, new FindAnnotations("@org.springframework.boot.autoconfigure.SpringBootApplication", null).getVisitor());
    }

}

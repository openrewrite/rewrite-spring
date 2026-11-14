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
package org.openrewrite.java.flyway;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.AddDependencyVisitor;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddFlywayModuleWithScope extends Recipe {
    private static final String FLYWAY_GROUP_ID = "org.flywaydb";

    @Option(displayName = "Module artifact ID",
            description = "The Flyway database module to add.",
            example = "flyway-database-postgresql")
    String artifactId;

    String displayName = "Add Flyway database module with matching scope";
    String description = "Add the Flyway database module in the narrowest Maven scope or Gradle configuration " +
                         "that has `flyway-core` on its classpath.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Xml.Document) {
                    return addToMaven((Xml.Document) tree, ctx);
                }
                if (tree instanceof JavaSourceFile && new IsBuildGradle<ExecutionContext>(true).visit(tree, ctx) != tree) {
                    return addToGradle((JavaSourceFile) tree, ctx);
                }
                return tree;
            }
        };
    }

    private Xml.Document addToMaven(Xml.Document pom, ExecutionContext ctx) {
        MavenResolutionResult mrr = pom.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
        if (mrr == null) {
            return pom;
        }
        return scopeToAddModuleTo((scope, artifact) -> !mrr.findDependencies(FLYWAY_GROUP_ID, artifact, scope).isEmpty())
                .map(scope -> (Xml.Document) new org.openrewrite.maven.AddDependencyVisitor(
                        FLYWAY_GROUP_ID, artifactId, "latest.release", null, scope.name().toLowerCase(),
                        null, null, null, null, null).visitNonNull(pom, ctx))
                .orElse(pom);
    }

    private JavaSourceFile addToGradle(JavaSourceFile buildFile, ExecutionContext ctx) {
        GradleProject gp = buildFile.getMarkers().findFirst(GradleProject.class).orElse(null);
        if (gp == null) {
            return buildFile;
        }
        return scopeToAddModuleTo((scope, artifact) -> {
            GradleDependencyConfiguration classpath = gp.getConfiguration(Scope.asResolvableGradleConfigurationName(scope));
            return classpath != null && classpath.findResolvedDependency(FLYWAY_GROUP_ID, artifact) != null;
        })
                .map(scope -> (JavaSourceFile) new AddDependencyVisitor(
                        FLYWAY_GROUP_ID, artifactId, null, null, Scope.asConsumableGradleConfigurationName(scope),
                        null, null, null, AddFlywayModuleWithScope::isTopLevel, null).visitNonNull(buildFile, ctx))
                .orElse(buildFile);
    }

    private Optional<Scope> scopeToAddModuleTo(BiPredicate<Scope, String> isOnClasspath) {
        return Stream.of(Scope.Compile, Scope.Runtime, Scope.Test)
                .filter(scope -> isOnClasspath.test(scope, "flyway-core"))
                .findFirst()
                .filter(scope -> !isOnClasspath.test(scope, artifactId));
    }

    private static boolean isTopLevel(Cursor cursor) {
        if (cursor.getValue() instanceof J.Block) {
            return cursor.getParentOrThrow().getValue() instanceof JavaSourceFile;
        }
        return cursor.getParentOrThrow().firstEnclosing(J.MethodInvocation.class) == null;
    }
}

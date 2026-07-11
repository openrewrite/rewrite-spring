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
package org.openrewrite.gradle.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.gradle.marker.GradlePluginDescriptor;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.tree.K;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddParametersCompilerFlagToGradle extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add `-parameters` compiler flag for Spring in Gradle";
    }

    @Override
    public String getDescription() {
        return "Adds `options.compilerArgs.add(\"-parameters\")` to `JavaCompile` tasks and " +
                "`compilerOptions.javaParameters = true` to Kotlin compile tasks when the corresponding " +
                "plugins are applied. Spring uses parameter name retention for dependency injection. " +
                "Projects using the Spring Boot Gradle plugin already have both flags configured and are not modified.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsBuildGradle<>(), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof JavaSourceFile)) {
                    return super.visit(tree, ctx);
                }
                JavaSourceFile sourceFile = (JavaSourceFile) tree;
                GradleProject gradleProject = sourceFile.getMarkers().findFirst(GradleProject.class).orElse(null);
                // The Spring Boot Gradle plugin already configures -parameters/-java-parameters on all compile tasks
                if (gradleProject == null || hasBootPlugin(gradleProject)) {
                    return sourceFile;
                }

                boolean kotlin = hasKotlinPlugin(gradleProject);
                ExistingFlags present = existingFlags(sourceFile);
                boolean addJavaFlag = (hasJavaPlugin(gradleProject) || kotlin) && !present.javaFlag;
                boolean addKotlinFlag = kotlin && !present.kotlinFlag;
                if (!addJavaFlag && !addKotlinFlag) {
                    return sourceFile;
                }

                if (sourceFile instanceof G.CompilationUnit) {
                    return GroovyDsl.addParametersFlags((G.CompilationUnit) sourceFile, addJavaFlag, addKotlinFlag, ctx);
                }
                if (sourceFile instanceof K.CompilationUnit) {
                    return KotlinDsl.addParametersFlags((K.CompilationUnit) sourceFile, addJavaFlag, addKotlinFlag, ctx);
                }
                return sourceFile;
            }
        });
    }

    /**
     * Which flags the build file already configures: the Java `-parameters` flag, and the Kotlin
     * equivalent (`-java-parameters` free arg or the first-class `javaParameters` compiler option).
     * Would be a record if this library did not target Java 8.
     */
    private static class ExistingFlags {
        boolean javaFlag;
        boolean kotlinFlag;
    }

    private static ExistingFlags existingFlags(JavaSourceFile sourceFile) {
        ExistingFlags found = new ExistingFlags();
        new JavaIsoVisitor<ExistingFlags>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExistingFlags acc) {
                if ("-parameters".equals(literal.getValue())) {
                    acc.javaFlag = true;
                } else if ("-java-parameters".equals(literal.getValue())) {
                    acc.kotlinFlag = true;
                }
                return literal;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExistingFlags acc) {
                if ("javaParameters".equals(identifier.getSimpleName())) {
                    acc.kotlinFlag = true;
                }
                return identifier;
            }
        }.visit(sourceFile, found);
        return found;
    }

    private static boolean hasBootPlugin(GradleProject gradleProject) {
        return gradleProject
                .getPlugins()
                .stream()
                .anyMatch(AddParametersCompilerFlagToGradle::isBootPlugin);
    }

    private static boolean isBootPlugin(GradlePluginDescriptor p) {
        return "org.springframework.boot".equals(p.getId())
                || p.getFullyQualifiedClassName().startsWith("org.springframework.boot.");
    }

    private static boolean hasJavaPlugin(GradleProject gradleProject) {
        return gradleProject
                .getPlugins()
                .stream()
                .anyMatch(AddParametersCompilerFlagToGradle::isJavaPlugin);
    }

    private static boolean isJavaPlugin(GradlePluginDescriptor p) {
        return "java".equals(p.getId())
                || "org.gradle.java".equals(p.getId())
                || "org.gradle.api.plugins.JavaPlugin".equals(p.getFullyQualifiedClassName());
    }

    private static boolean hasKotlinPlugin(GradleProject gradleProject) {
        return gradleProject
                .getPlugins()
                .stream()
                .anyMatch(AddParametersCompilerFlagToGradle::isKotlinPlugin);
    }

    private static boolean isKotlinPlugin(GradlePluginDescriptor p) {
        return "kotlin".equals(p.getId())
                || p.getId() != null && p.getId().startsWith("org.jetbrains.kotlin.")
                || p.getFullyQualifiedClassName().startsWith("org.jetbrains.kotlin.");
    }

    /**
     * Appends the compiler-flag task configuration blocks to a Groovy DSL build file.
     */
    private static class GroovyDsl {

        private static G.CompilationUnit addParametersFlags(
                G.CompilationUnit cu,
                boolean addJavaFlag,
                boolean addKotlinFlag,
                ExecutionContext ctx) {

            String snippet = createSnippet(addJavaFlag, addKotlinFlag);

            SourceFile parsed = GradleParser.builder().build()
                    .parse(ctx, snippet)
                    .findFirst()
                    .orElse(null);
            if (!(parsed instanceof G.CompilationUnit)) {
                // Snippet could not be parsed (e.g. Gradle API unresolvable in this environment)
                return cu;
            }
            return cu.withStatements(ListUtils.concatAll(cu.getStatements(), ((G.CompilationUnit) parsed).getStatements()));
        }

        private static String createSnippet(boolean addJavaFlag, boolean addKotlinFlag) {
            String snippet = "";
            if (addJavaFlag) {
                snippet += "\n\ntasks.withType(JavaCompile).configureEach {\n" +
                        "    options.compilerArgs.add('-parameters')\n" +
                        "}";
            }
            if (addKotlinFlag) {
                snippet += (snippet.isEmpty() ? "\n" : "") +
                        "\ntasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {\n" +
                        "    compilerOptions.javaParameters = true\n" +
                        "}";
            }
            return snippet;
        }
    }

    /**
     * Appends the compiler-flag task configuration blocks to a Kotlin DSL (.gradle.kts) build file.
     */
    private static class KotlinDsl {

        private static K.CompilationUnit addParametersFlags(
                K.CompilationUnit cu,
                boolean addJavaFlag,
                boolean addKotlinFlag,
                ExecutionContext ctx) {

            String snippet = createSnippet(addJavaFlag, addKotlinFlag);

            SourceFile parsed = KotlinParser.builder()
                    .isKotlinScript(true)
                    .build()
                    .parse(ctx, snippet)
                    .findFirst()
                    .orElse(null);
            if (!(parsed instanceof K.CompilationUnit)) {
                // Snippet could not be parsed; leave the build file unchanged rather than fail the run
                return cu;
            }
            return cu.withStatements(ListUtils.concatAll(cu.getStatements(), ((K.CompilationUnit) parsed).getStatements()));
        }

        private static String createSnippet(boolean addJavaFlag, boolean addKotlinFlag) {
            String snippet = "";
            if (addJavaFlag) {
                snippet += "\n\ntasks.withType<JavaCompile>().configureEach {\n" +
                        "    options.compilerArgs.add(\"-parameters\")\n" +
                        "}";
            }
            if (addKotlinFlag) {
                snippet += (snippet.isEmpty() ? "\n" : "") +
                        "\ntasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {\n" +
                        "    compilerOptions.javaParameters.set(true)\n" +
                        "}";
            }
            return snippet;
        }
    }
}

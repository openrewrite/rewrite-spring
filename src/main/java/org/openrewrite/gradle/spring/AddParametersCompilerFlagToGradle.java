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
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.tree.K;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddParametersCompilerFlagToGradle extends Recipe {

    final String displayName = "Add `-parameters` compiler flag for Spring in Gradle";

    final String description = "Adds `options.compilerArgs.add(\"-parameters\")` to `JavaCompile` tasks and, when the Kotlin " +
            "Gradle Plugin version can be determined from this file's `plugins {}` block, the matching " +
            "`javaParameters` flag (`compilerOptions.javaParameters` on 1.8+, `kotlinOptions.javaParameters` " +
            "on older versions) to Kotlin compile tasks. Spring uses parameter name retention for dependency " +
            "injection. Projects using the Spring Boot Gradle plugin already have both flags configured and " +
            "are not modified. When the Kotlin plugin's version can't be determined from this file (version " +
            "catalogs, convention plugins, buildscript classpath declarations) the Kotlin flag is left alone " +
            "rather than risk emitting a form the applied plugin doesn't support.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsBuildGradle<>(true), new JavaIsoVisitor<ExecutionContext>() {

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
                KotlinOptionsForm kotlinForm = kotlin && !present.kotlinFlag ?
                        kotlinOptionsForm(sourceFile) : KotlinOptionsForm.UNKNOWN;
                if (!addJavaFlag && kotlinForm == KotlinOptionsForm.UNKNOWN) {
                    return sourceFile;
                }

                if (sourceFile instanceof G.CompilationUnit) {
                    return addParametersFlags((G.CompilationUnit) sourceFile, addJavaFlag, kotlinForm, ctx);
                }
                if (sourceFile instanceof K.CompilationUnit) {
                    return addParametersFlags((K.CompilationUnit) sourceFile, addJavaFlag, kotlinForm, ctx);
                }
                return sourceFile;
            }
        });
    }

    /**
     * Kotlin Gradle Plugin 1.8+ understands {@code compilerOptions.javaParameters}; older versions only
     * {@code kotlinOptions.javaParameters}. {@code UNKNOWN} when the applied version can't be determined
     * from this file, in which case no Kotlin flag is emitted rather than guessing at a form.
     */
    private enum KotlinOptionsForm {
        COMPILER_OPTIONS, KOTLIN_OPTIONS, UNKNOWN
    }

    private static class ExistingFlags {
        boolean javaFlag;
        boolean kotlinFlag;
    }

    private static ExistingFlags existingFlags(JavaSourceFile sourceFile) {
        return new JavaIsoVisitor<ExistingFlags>() {
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
        }.reduce(sourceFile, new ExistingFlags());
    }

    private static boolean hasBootPlugin(GradleProject gradleProject) {
        return gradleProject
                .getPlugins()
                .stream()
                .anyMatch(p -> "org.springframework.boot".equals(p.getId()) ||
                        p.getFullyQualifiedClassName().startsWith("org.springframework.boot."));
    }

    private static boolean hasJavaPlugin(GradleProject gradleProject) {
        return gradleProject
                .getPlugins()
                .stream()
                .anyMatch(p -> "java".equals(p.getId()) ||
                        "org.gradle.java".equals(p.getId()) ||
                        p.getFullyQualifiedClassName().startsWith("org.gradle.api.plugins.JavaPlugin"));
    }

    private static boolean hasKotlinPlugin(GradleProject gradleProject) {
        return gradleProject
                .getPlugins()
                .stream()
                .anyMatch(p -> "kotlin".equals(p.getId()) ||
                        (p.getId() != null && p.getId().startsWith("org.jetbrains.kotlin.")) ||
                        p.getFullyQualifiedClassName().startsWith("org.jetbrains.kotlin."));
    }

    private static KotlinOptionsForm kotlinOptionsForm(JavaSourceFile sourceFile) {
        String version = new JavaIsoVisitor<AtomicReference<String>>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicReference<String> acc) {
                J.MethodInvocation m = super.visitMethodInvocation(method, acc);
                if ("version".equals(m.getSimpleName()) && isKotlinPluginSelect(m.getSelect())) {
                    String version = singleStringArgument(m.getArguments());
                    if (version != null) {
                        acc.set(version);
                    }
                }
                return m;
            }
        }.reduce(sourceFile, new AtomicReference<String>()).get();
        return version == null ? KotlinOptionsForm.UNKNOWN : kotlinOptionsForm(version);
    }

    private static boolean isKotlinPluginSelect(@Nullable Expression select) {
        if (!(select instanceof J.MethodInvocation)) {
            return false;
        }
        J.MethodInvocation call = (J.MethodInvocation) select;
        if ("kotlin".equals(call.getSimpleName())) {
            return true;
        }
        String id = singleStringArgument(call.getArguments());
        return "id".equals(call.getSimpleName()) && id != null &&
                ("kotlin".equals(id) || id.startsWith("org.jetbrains.kotlin."));
    }

    private static @Nullable String singleStringArgument(List<Expression> arguments) {
        if (arguments.size() == 1 && arguments.get(0) instanceof J.Literal) {
            Object value = ((J.Literal) arguments.get(0)).getValue();
            return value instanceof String ? (String) value : null;
        }
        return null;
    }

    private static KotlinOptionsForm kotlinOptionsForm(String version) {
        String[] parts = version.split("\\.");
        if (parts.length < 2) {
            return KotlinOptionsForm.UNKNOWN;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 1 || (major == 1 && minor >= 8) ?
                    KotlinOptionsForm.COMPILER_OPTIONS : KotlinOptionsForm.KOTLIN_OPTIONS;
        } catch (NumberFormatException e) {
            return KotlinOptionsForm.UNKNOWN;
        }
    }

    private static G.CompilationUnit addParametersFlags(
            G.CompilationUnit cu,
            boolean addJavaFlag,
            KotlinOptionsForm kotlinForm,
            ExecutionContext ctx) {

        String snippet = "";
        if (addJavaFlag) {
            snippet += "\n\ntasks.withType(JavaCompile).configureEach {\n" +
                    "    options.compilerArgs.add('-parameters')\n" +
                    "}";
        }
        if (kotlinForm != KotlinOptionsForm.UNKNOWN) {
            String option = kotlinForm == KotlinOptionsForm.COMPILER_OPTIONS ?
                    "compilerOptions.javaParameters" : "kotlinOptions.javaParameters";
            snippet += "\n\ntasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {\n" +
                    "    " + option + " = true\n" +
                    "}";
        }

        SourceFile parsed = GradleParser.builder().build()
                .parse(ctx, snippet)
                .findFirst()
                .orElse(null);
        if (!(parsed instanceof G.CompilationUnit)) {
            return cu;
        }
        return cu.withStatements(ListUtils.concatAll(cu.getStatements(), ((G.CompilationUnit) parsed).getStatements()));
    }

    private static K.CompilationUnit addParametersFlags(
            K.CompilationUnit cu,
            boolean addJavaFlag,
            KotlinOptionsForm kotlinForm,
            ExecutionContext ctx) {

        String snippet = "";
        if (addJavaFlag) {
            snippet += "\n\ntasks.withType<JavaCompile>().configureEach {\n" +
                    "    options.compilerArgs.add(\"-parameters\")\n" +
                    "}";
        }
        if (kotlinForm != KotlinOptionsForm.UNKNOWN) {
            String option = kotlinForm == KotlinOptionsForm.COMPILER_OPTIONS ?
                    "compilerOptions.javaParameters.set(true)" : "kotlinOptions.javaParameters = true";
            snippet += "\n\ntasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {\n" +
                    "    " + option + "\n" +
                    "}";
        }

        SourceFile parsed = KotlinParser.builder()
                .isKotlinScript(true)
                .build()
                .parse(ctx, snippet)
                .findFirst()
                .orElse(null);
        if (!(parsed instanceof K.CompilationUnit)) {
            return cu;
        }
        return cu.withStatements(ListUtils.concatAll(cu.getStatements(), ((K.CompilationUnit) parsed).getStatements()));
    }
}

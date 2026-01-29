/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.batch;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static java.util.Collections.singletonList;

public class AddTransactionManagerToTaskletAndChunk extends Recipe {

    private static final String TASKLET_1ARG = "org.springframework.batch.core.step.builder.StepBuilder tasklet(org.springframework.batch.core.step.tasklet.Tasklet)";
    private static final String CHUNK_INT = "org.springframework.batch.core.step.builder.StepBuilder chunk(int)";
    private static final String CHUNK_POLICY = "org.springframework.batch.core.step.builder.StepBuilder chunk(org.springframework.batch.repeat.CompletionPolicy)";

    @Getter
    final String displayName = "Add `PlatformTransactionManager` to `tasklet()` and `chunk()` calls";

    @Getter
    final String description = "Spring Batch 5.0 requires a `PlatformTransactionManager` as the second argument to " +
            "`StepBuilder.tasklet(Tasklet)` and `StepBuilder.chunk(int)` / `StepBuilder.chunk(CompletionPolicy)`. " +
            "This recipe adds the `transactionManager` argument and injects it as a method parameter if needed.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(TASKLET_1ARG),
                        new UsesMethod<>(CHUNK_INT),
                        new UsesMethod<>(CHUNK_POLICY)
                ),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                        tree = new AddTransactionManagerParameterVisitor().visit(tree, ctx);
                        return new AddTransactionManagerArgumentVisitor().visit(tree, ctx);
                    }
                }
        );
    }

    private static boolean usesOneArgTaskletOrChunk(J tree) {
        return !FindMethods.find(tree, TASKLET_1ARG).isEmpty() ||
                !FindMethods.find(tree, CHUNK_INT).isEmpty() ||
                !FindMethods.find(tree, CHUNK_POLICY).isEmpty();
    }

    private static class AddTransactionManagerParameterVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
            if (usesOneArgTaskletOrChunk(md) &&
                    md.getParameters().stream().noneMatch(AddTransactionManagerToTaskletAndChunk::isTransactionManagerParameter) &&
                    !md.isConstructor()) {
                maybeAddImport("org.springframework.transaction.PlatformTransactionManager");
                List<Statement> params = md.getParameters();
                boolean parametersEmpty = params.isEmpty() || params.get(0) instanceof J.Empty;
                J.VariableDeclarations vdd = JavaTemplate.builder("PlatformTransactionManager transactionManager")
                        .contextSensitive()
                        .imports("org.springframework.transaction.PlatformTransactionManager")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-tx-5.+"))
                        .build()
                        .<J.MethodDeclaration>apply(getCursor(), md.getCoordinates().replaceParameters())
                        .getParameters().get(0).withPrefix(parametersEmpty ? Space.EMPTY : Space.SINGLE_SPACE);
                if (parametersEmpty) {
                    return md.withParameters(singletonList(vdd))
                            .withMethodType(md.getMethodType()
                                    .withParameterTypes(singletonList(vdd.getType())));
                }
                return md.withParameters(ListUtils.concat(params, vdd))
                        .withMethodType(md.getMethodType()
                                .withParameterTypes(ListUtils.concat(md.getMethodType().getParameterTypes(), vdd.getType())));
            }
            return super.visitMethodDeclaration(md, ctx);
        }
    }

    private static boolean isTransactionManagerParameter(Statement statement) {
        return statement instanceof J.VariableDeclarations &&
                TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(),
                        "org.springframework.transaction.PlatformTransactionManager");
    }

    private static class AddTransactionManagerArgumentVisitor extends JavaVisitor<ExecutionContext> {
        private static final MethodMatcher TASKLET_MATCHER = new MethodMatcher(TASKLET_1ARG);
        private static final MethodMatcher CHUNK_INT_MATCHER = new MethodMatcher(CHUNK_INT);
        private static final MethodMatcher CHUNK_POLICY_MATCHER = new MethodMatcher(CHUNK_POLICY);

        private String findTransactionManagerParameterName() {
            Cursor cursor = getCursor();
            while (cursor != null) {
                Object value = cursor.getValue();
                if (value instanceof J.MethodDeclaration) {
                    J.MethodDeclaration md = (J.MethodDeclaration) value;
                    return md.getParameters().stream()
                            .filter(AddTransactionManagerToTaskletAndChunk::isTransactionManagerParameter)
                            .map(p -> ((J.VariableDeclarations) p).getVariables().get(0).getSimpleName())
                            .findFirst()
                            .orElse("transactionManager");
                }
                cursor = cursor.getParent();
            }
            return "transactionManager";
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            String tmName = findTransactionManagerParameterName();
            if (TASKLET_MATCHER.matches(mi)) {
                return JavaTemplate.builder("#{any(org.springframework.batch.core.step.tasklet.Tasklet)}, " + tmName)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-tx-5.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replaceArguments(), mi.getArguments().get(0));
            }
            if (CHUNK_INT_MATCHER.matches(mi)) {
                return JavaTemplate.builder("#{any(int)}, " + tmName)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-tx-5.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replaceArguments(), mi.getArguments().get(0));
            }
            if (CHUNK_POLICY_MATCHER.matches(mi)) {
                return JavaTemplate.builder("#{any(org.springframework.batch.repeat.CompletionPolicy)}, " + tmName)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-batch-infrastructure-5.1.+", "spring-tx-5.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replaceArguments(), mi.getArguments().get(0));
            }
            return super.visitMethodInvocation(mi, ctx);
        }
    }
}

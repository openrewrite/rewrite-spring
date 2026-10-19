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
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singletonList;

public class AddTransactionManagerToTaskletAndChunk extends Recipe {

    private static final String TASKLET_1ARG = "org.springframework.batch.core.step.builder.StepBuilder tasklet(org.springframework.batch.core.step.tasklet.Tasklet)";
    private static final String CHUNK_INT = "org.springframework.batch.core.step.builder.StepBuilder chunk(int)";
    private static final String CHUNK_POLICY = "org.springframework.batch.core.step.builder.StepBuilder chunk(org.springframework.batch.repeat.CompletionPolicy)";
    private static final MethodMatcher TASKLET_MATCHER = new MethodMatcher(TASKLET_1ARG);
    private static final MethodMatcher CHUNK_INT_MATCHER = new MethodMatcher(CHUNK_INT);
    private static final MethodMatcher CHUNK_POLICY_MATCHER = new MethodMatcher(CHUNK_POLICY);
    private static final MethodMatcher TRANSACTION_MANAGER_MATCHER = new MethodMatcher("org.springframework.batch.core.step.builder.* transactionManager(..)");

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

    private static class AddTransactionManagerParameterVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static boolean needsTransactionManagerArgument(J tree) {
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean needed) {
                    boolean matchesAny = TASKLET_MATCHER.matches(method) || CHUNK_INT_MATCHER.matches(method) || CHUNK_POLICY_MATCHER.matches(method);
                    if (matchesAny && !chainConfiguresTransactionManager(getCursor())) {
                        needed.set(true);
                    }
                    return super.visitMethodInvocation(method, needed);
                }
            }.reduce(tree, new AtomicBoolean()).get();
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
            if (needsTransactionManagerArgument(md) &&
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
                TypeUtils.isAssignableTo("org.springframework.transaction.PlatformTransactionManager",
                        ((J.VariableDeclarations) statement).getType());
    }

    private static boolean chainConfiguresTransactionManager(Cursor cursor) {
        while (true) {
            Cursor parent = cursor.getParentTreeCursor();
            Object parentValue = parent.getValue();
            if (!(parentValue instanceof J.MethodInvocation)) {
                return false;
            }
            J.MethodInvocation parentMi = (J.MethodInvocation) parentValue;
            if (parentMi.getSelect() != cursor.getValue()) {
                return false;
            }
            if (TRANSACTION_MANAGER_MATCHER.matches(parentMi)) {
                return true;
            }
            cursor = parent;
        }
    }

    private static class AddTransactionManagerArgumentVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            if (chainConfiguresTransactionManager(getCursor())) {
                return super.visitMethodInvocation(mi, ctx);
            }
            J.Identifier transactionManager = findTransactionManagerParameter();
            if (transactionManager == null) {
                return super.visitMethodInvocation(mi, ctx);
            }
            if (TASKLET_MATCHER.matches(mi)) {
                return JavaTemplate.builder("#{any(org.springframework.batch.core.step.tasklet.Tasklet)}, #{any(org.springframework.transaction.PlatformTransactionManager)}")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-tx-5.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replaceArguments(), mi.getArguments().get(0), transactionManager);
            }
            if (CHUNK_INT_MATCHER.matches(mi)) {
                return JavaTemplate.builder("#{any(int)}, #{any(org.springframework.transaction.PlatformTransactionManager)}")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-tx-5.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replaceArguments(), mi.getArguments().get(0), transactionManager);
            }
            if (CHUNK_POLICY_MATCHER.matches(mi)) {
                return JavaTemplate.builder("#{any(org.springframework.batch.repeat.CompletionPolicy)}, #{any(org.springframework.transaction.PlatformTransactionManager)}")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx,
                                        "spring-batch-core-5.1.+",
                                        "spring-batch-infrastructure-5.1.+",
                                        "spring-tx-5.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replaceArguments(), mi.getArguments().get(0), transactionManager);
            }
            return super.visitMethodInvocation(mi, ctx);
        }

        private J.@Nullable Identifier findTransactionManagerParameter() {
            Cursor cursor = getCursor();
            while (cursor != null) {
                Object value = cursor.getValue();
                if (value instanceof J.MethodDeclaration) {
                    J.MethodDeclaration md = (J.MethodDeclaration) value;
                    return md.getParameters().stream()
                            .filter(AddTransactionManagerToTaskletAndChunk::isTransactionManagerParameter)
                            .map(p -> ((J.VariableDeclarations) p).getVariables().get(0).getName()
                                    .withId(Tree.randomId()).withPrefix(Space.EMPTY))
                            .findFirst()
                            .orElse(null);
                }
                cursor = cursor.getParent();
            }
            return null;
        }
    }
}

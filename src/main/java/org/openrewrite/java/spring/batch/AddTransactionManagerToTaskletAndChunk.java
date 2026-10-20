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
import org.openrewrite.java.tree.*;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class AddTransactionManagerToTaskletAndChunk extends Recipe {

    private static final String STEP_BUILDER = "org.springframework.batch.core.step.builder.StepBuilder";
    private static final String TASKLET = "org.springframework.batch.core.step.tasklet.Tasklet";
    private static final String COMPLETION_POLICY = "org.springframework.batch.repeat.CompletionPolicy";
    private static final String PLATFORM_TRANSACTION_MANAGER = "org.springframework.transaction.PlatformTransactionManager";

    private static final MethodMatcher TASKLET_MATCHER = new MethodMatcher(STEP_BUILDER + " tasklet(" + TASKLET + ")");
    private static final MethodMatcher CHUNK_INT_MATCHER = new MethodMatcher(STEP_BUILDER + " chunk(int)");
    private static final MethodMatcher CHUNK_POLICY_MATCHER = new MethodMatcher(STEP_BUILDER + " chunk(" + COMPLETION_POLICY + ")");
    private static final MethodMatcher TRANSACTION_MANAGER_MATCHER = new MethodMatcher("org.springframework.batch.core.step.builder.* transactionManager(..)");

    private static final String MSG_NEW_PARAMETER = "newTransactionManagerParameter";

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
                        new UsesMethod<>(TASKLET_MATCHER),
                        new UsesMethod<>(CHUNK_INT_MATCHER),
                        new UsesMethod<>(CHUNK_POLICY_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        boolean invocationMatches = TASKLET_MATCHER.matches(mi) || CHUNK_INT_MATCHER.matches(mi) || CHUNK_POLICY_MATCHER.matches(mi);
                        if (!invocationMatches || chainConfiguresTransactionManager(getCursor())) {
                            return mi;
                        }
                        J.Identifier transactionManager = transactionManagerArgument(ctx);
                        if (transactionManager == null) {
                            return mi;
                        }
                        return JavaTemplate.builder("#{any()}, #{any(" + PLATFORM_TRANSACTION_MANAGER + ")}")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                        "spring-batch-core-5.1", "spring-batch-infrastructure-5.1", "spring-tx-5"))
                                .build()
                                .apply(updateCursor(mi), mi.getCoordinates().replaceArguments(),
                                        mi.getArguments().get(0), transactionManager.withId(Tree.randomId()));
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        J.VariableDeclarations parameter = getCursor().getMessage(MSG_NEW_PARAMETER);
                        return parameter == null ? md : addParameter(md, parameter);
                    }

                    private J.@Nullable Identifier transactionManagerArgument(ExecutionContext ctx) {
                        Cursor declaration = enclosingMethod(getCursor());
                        if (declaration == null) {
                            return null;
                        }
                        J.MethodDeclaration md = declaration.getValue();
                        J.VariableDeclarations parameter = transactionManagerParameter(md);
                        if (parameter == null) {
                            if (md.isConstructor() || md.getMethodType() == null) {
                                return null;
                            }
                            parameter = declaration.computeMessageIfAbsent(MSG_NEW_PARAMETER, k -> newTransactionManagerParameter(declaration, ctx));
                        }
                        return parameter.getVariables().get(0).getName();
                    }

                    private J.MethodDeclaration addParameter(J.MethodDeclaration md, J.VariableDeclarations parameter) {
                        maybeAddImport(PLATFORM_TRANSACTION_MANAGER);
                        List<Statement> parameters = ListUtils.filter(md.getParameters(), p -> !(p instanceof J.Empty));
                        JavaType.Method type = requireNonNull(md.getMethodType());
                        JavaType.Method withParameter = type
                                .withParameterTypes(ListUtils.concat(type.getParameterTypes(), parameter.getType()))
                                .withParameterNames(ListUtils.concat(type.getParameterNames(), "transactionManager"));
                        return md.withParameters(ListUtils.concat(parameters,
                                        parameter.withPrefix(parameters.isEmpty() ? Space.EMPTY : Space.SINGLE_SPACE)))
                                .withMethodType(withParameter)
                                .withName(md.getName().withType(withParameter));
                    }
                }
        );
    }

    private static J.VariableDeclarations newTransactionManagerParameter(Cursor declaration, ExecutionContext ctx) {
        J.MethodDeclaration md = declaration.getValue();
        return (J.VariableDeclarations) JavaTemplate.builder("PlatformTransactionManager transactionManager")
                .imports(PLATFORM_TRANSACTION_MANAGER)
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-tx-5"))
                .build()
                .<J.MethodDeclaration>apply(declaration, md.getCoordinates().replaceParameters())
                .getParameters().get(0);
    }

    private static @Nullable Cursor enclosingMethod(Cursor cursor) {
        Cursor method = cursor.dropParentUntil(it -> it instanceof J.MethodDeclaration || it == Cursor.ROOT_VALUE);
        if (!(method.getValue() instanceof J.MethodDeclaration)) {
            return null;
        }
        Cursor classBody = method.getParentTreeCursor();
        boolean localOrAnonymous = classBody.getParentTreeCursor().getValue() instanceof J.NewClass ||
                classBody.firstEnclosing(J.MethodDeclaration.class) != null;
        return localOrAnonymous ? enclosingMethod(method) : method;
    }

    private static boolean chainConfiguresTransactionManager(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        J.MethodInvocation chained = parent.getValue() instanceof J.MethodInvocation ? parent.getValue() : null;
        if (chained == null || chained.getSelect() != cursor.getValue()) {
            return false;
        }
        return TRANSACTION_MANAGER_MATCHER.matches(chained) || chainConfiguresTransactionManager(parent);
    }

    private static J.@Nullable VariableDeclarations transactionManagerParameter(J.MethodDeclaration md) {
        for (Statement parameter : md.getParameters()) {
            if (parameter instanceof J.VariableDeclarations &&
                    TypeUtils.isAssignableTo(PLATFORM_TRANSACTION_MANAGER, ((J.VariableDeclarations) parameter).getType())) {
                return (J.VariableDeclarations) parameter;
            }
        }
        return null;
    }
}

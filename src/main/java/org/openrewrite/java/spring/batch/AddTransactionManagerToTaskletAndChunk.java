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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;

public class AddTransactionManagerToTaskletAndChunk extends Recipe {

    private static final String STEP_BUILDER = "org.springframework.batch.core.step.builder.StepBuilder";
    private static final String PLATFORM_TRANSACTION_MANAGER = "org.springframework.transaction.PlatformTransactionManager";

    private static final MethodMatcher TASKLET_MATCHER = new MethodMatcher(STEP_BUILDER + " tasklet(org.springframework.batch.core.step.tasklet.Tasklet)");
    private static final MethodMatcher CHUNK_INT_MATCHER = new MethodMatcher(STEP_BUILDER + " chunk(int)");
    private static final MethodMatcher CHUNK_POLICY_MATCHER = new MethodMatcher(STEP_BUILDER + " chunk(org.springframework.batch.repeat.CompletionPolicy)");
    private static final MethodMatcher TRANSACTION_MANAGER_MATCHER = new MethodMatcher("org.springframework.batch.core.step.builder.* transactionManager(..)");

    private static final String MSG_ADD_PARAMETER = "addTransactionManagerParameter";

    @Getter
    final String displayName = "Add `PlatformTransactionManager` to `tasklet()` and `chunk()` calls";

    @Getter
    final String description = "Spring Batch 5.0 requires a `PlatformTransactionManager` as the second argument to " +
            "`StepBuilder.tasklet(Tasklet)` and `StepBuilder.chunk(int)` / `StepBuilder.chunk(CompletionPolicy)`. " +
            "This recipe passes a `PlatformTransactionManager` parameter or field that is in scope, " +
            "and otherwise adds a `transactionManager` method parameter.";

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
                        boolean taskletOrChunk = TASKLET_MATCHER.matches(mi) || CHUNK_INT_MATCHER.matches(mi) || CHUNK_POLICY_MATCHER.matches(mi);
                        if (!taskletOrChunk || chainConfiguresTransactionManager(getCursor())) {
                            return mi;
                        }
                        Cursor methodCursor = enclosingMethod(getCursor());
                        if (methodCursor == null) {
                            return mi;
                        }
                        J.Identifier transactionManager = transactionManagerInScope(methodCursor);
                        if (transactionManager == null) {
                            J.MethodDeclaration md = methodCursor.getValue();
                            if (md.isConstructor() || md.getMethodType() == null) {
                                return mi;
                            }
                            methodCursor.putMessage(MSG_ADD_PARAMETER, true);
                            transactionManager = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                                    "transactionManager", JavaType.ShallowClass.build(PLATFORM_TRANSACTION_MANAGER), null);
                        }
                        String firstParameterType = TypeUtils.toString(requireNonNull(mi.getMethodType()).getParameterTypes().get(0));
                        return JavaTemplate.builder("#{any(" + firstParameterType + ")}, #{any(" + PLATFORM_TRANSACTION_MANAGER + ")}")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                        "spring-batch-core-5.1", "spring-batch-infrastructure-5.1", "spring-tx-5"))
                                .build()
                                .apply(updateCursor(mi), mi.getCoordinates().replaceArguments(),
                                        mi.getArguments().get(0), transactionManager.withId(randomId()));
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        if (getCursor().getMessage(MSG_ADD_PARAMETER) == null) {
                            return md;
                        }
                        maybeAddImport(PLATFORM_TRANSACTION_MANAGER);
                        J.VariableDeclarations parameter = (J.VariableDeclarations) JavaTemplate.builder("PlatformTransactionManager transactionManager")
                                .imports(PLATFORM_TRANSACTION_MANAGER)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-tx-5"))
                                .build()
                                .<J.MethodDeclaration>apply(updateCursor(md), md.getCoordinates().replaceParameters())
                                .getParameters().get(0);
                        List<Statement> parameters = ListUtils.filter(md.getParameters(), p -> !(p instanceof J.Empty));
                        JavaType.Method methodType = requireNonNull(md.getMethodType());
                        JavaType.Method newMethodType = methodType
                                .withParameterTypes(ListUtils.concat(methodType.getParameterTypes(), parameter.getType()))
                                .withParameterNames(ListUtils.concat(methodType.getParameterNames(), "transactionManager"));
                        return md.withParameters(ListUtils.concat(parameters,
                                        parameter.withPrefix(parameters.isEmpty() ? Space.EMPTY : Space.SINGLE_SPACE)))
                                .withMethodType(newMethodType)
                                .withName(md.getName().withType(newMethodType));
                    }
                }
        );
    }

    private static @Nullable Cursor enclosingMethod(Cursor cursor) {
        Cursor method = cursor.dropParentUntil(it -> it instanceof J.MethodDeclaration || it == Cursor.ROOT_VALUE);
        while (method.getValue() instanceof J.MethodDeclaration) {
            Cursor classBody = method.getParentTreeCursor();
            boolean localOrAnonymous = classBody.getParentTreeCursor().getValue() instanceof J.NewClass ||
                    classBody.firstEnclosing(J.MethodDeclaration.class) != null;
            if (!localOrAnonymous) {
                return method;
            }
            method = method.dropParentUntil(it -> it instanceof J.MethodDeclaration || it == Cursor.ROOT_VALUE);
        }
        return null;
    }

    private static boolean chainConfiguresTransactionManager(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        while (parent.getValue() instanceof J.MethodInvocation &&
                ((J.MethodInvocation) parent.getValue()).getSelect() == cursor.getValue()) {
            if (TRANSACTION_MANAGER_MATCHER.matches((J.MethodInvocation) parent.getValue())) {
                return true;
            }
            cursor = parent;
            parent = parent.getParentTreeCursor();
        }
        return false;
    }

    private static J.@Nullable Identifier transactionManagerInScope(Cursor methodCursor) {
        J.MethodDeclaration md = methodCursor.getValue();
        for (Statement parameter : md.getParameters()) {
            if (parameter instanceof J.VariableDeclarations && isTransactionManager((J.VariableDeclarations) parameter)) {
                return ((J.VariableDeclarations) parameter).getVariables().get(0).getName();
            }
        }
        J.Block classBody = methodCursor.getParentTreeCursor().getValue();
        boolean staticMethod = md.hasModifier(J.Modifier.Type.Static);
        for (Statement statement : classBody.getStatements()) {
            if (statement instanceof J.VariableDeclarations && isTransactionManager((J.VariableDeclarations) statement) &&
                    (!staticMethod || ((J.VariableDeclarations) statement).hasModifier(J.Modifier.Type.Static))) {
                return ((J.VariableDeclarations) statement).getVariables().get(0).getName();
            }
        }
        return null;
    }

    private static boolean isTransactionManager(J.VariableDeclarations variable) {
        return TypeUtils.isAssignableTo(PLATFORM_TRANSACTION_MANAGER, variable.getType());
    }
}

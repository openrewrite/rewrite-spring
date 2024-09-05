/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.util.concurrent;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class ListenableFutureCallbackToBiConsumerVisitor extends JavaVisitor<ExecutionContext> {

    private static final String BI_CONSUMER = "java.util.function.BiConsumer";
    private static final String LISTENABLE_FUTURE_CALLBACK = "org.springframework.util.concurrent.ListenableFutureCallback";

    private static final MethodMatcher SUCCESS_CALLBACK_MATCHER = new MethodMatcher("org.springframework.util.concurrent.SuccessCallback onSuccess(..)", true);
    private static final MethodMatcher FAILURE_CALLBACK_MATCHER = new MethodMatcher("org.springframework.util.concurrent.FailureCallback onFailure(java.lang.Throwable)", true);

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
        if (!TypeUtils.isAssignableTo(LISTENABLE_FUTURE_CALLBACK, cd.getType())) {
            return cd;
        }

        J.MethodDeclaration successCallbackMethodDeclaration = extract(SUCCESS_CALLBACK_MATCHER, cd);
        J.MethodDeclaration failureCallbackMethodDeclaration = extract(FAILURE_CALLBACK_MATCHER, cd);
        if (successCallbackMethodDeclaration == null || failureCallbackMethodDeclaration == null) {
            return cd;
        }

        String template = String.format("class %s implements BiConsumer<%s, Throwable> {\n" +
                                        "    @Override\n" +
                                        "    public void accept(%s, %s) {\n" +
                                        "        if (ex == null) #{}\n" +
                                        "        else #{}\n" +
                                        "    }\n" +
                                        "}",
                cd.getSimpleName(),
                ((J.VariableDeclarations) successCallbackMethodDeclaration.getParameters().get(0)).getTypeExpression(),
                successCallbackMethodDeclaration.getParameters().get(0),
                failureCallbackMethodDeclaration.getParameters().get(0));
        //noinspection DataFlowIssue
        J.ClassDeclaration newClassDeclaration = JavaTemplate.builder(template)
                .contextSensitive()
                .imports(BI_CONSUMER)
                .build()
                .apply(updateCursor(cd), cd.getCoordinates().replace(),
                        successCallbackMethodDeclaration.getBody(),
                        failureCallbackMethodDeclaration.getBody());

        if (cd.getBody().getStatements().size() > 2) {
            //noinspection DataFlowIssue
            List<Statement> additionalStatements = ListUtils.map(cd.getBody().getStatements(), s ->
                    s == successCallbackMethodDeclaration || s == failureCallbackMethodDeclaration ? null : s);
            Statement acceptMethodWithPrefix = newClassDeclaration.getBody().getStatements().get(0)
                    .withPrefix(successCallbackMethodDeclaration.getPrefix());
            newClassDeclaration = newClassDeclaration.withBody(cd.getBody()
                    .withStatements(ListUtils.concat(additionalStatements, acceptMethodWithPrefix)));
        }

        maybeAddImport(BI_CONSUMER);
        maybeRemoveImport(LISTENABLE_FUTURE_CALLBACK);
        return newClassDeclaration
                .withLeadingAnnotations(cd.getLeadingAnnotations())
                .withModifiers(cd.getModifiers())
                .withPrefix(cd.getPrefix());
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
        if (!TypeUtils.isAssignableTo(LISTENABLE_FUTURE_CALLBACK, nc.getType())) {
            return nc;
        }

        J.MethodDeclaration successCallbackMethodDeclaration = extract(SUCCESS_CALLBACK_MATCHER, nc);
        J.MethodDeclaration failureCallbackMethodDeclaration = extract(FAILURE_CALLBACK_MATCHER, nc);
        if (successCallbackMethodDeclaration == null || failureCallbackMethodDeclaration == null) {
            return nc;
        }

        maybeRemoveImport(LISTENABLE_FUTURE_CALLBACK);
        if (nc.getBody().getStatements().size() > 2) {
            String template = String.format("new BiConsumer<%s, Throwable>() {\n" +
                                            "    @Override\n" +
                                            "    public void accept(%s, %s) {\n" +
                                            "        if (ex == null) #{}\n" +
                                            "        else #{}\n" +
                                            "    }\n" +
                                            "}",
                    ((J.VariableDeclarations) successCallbackMethodDeclaration.getParameters().get(0)).getTypeExpression(),
                    successCallbackMethodDeclaration.getParameters().get(0),
                    failureCallbackMethodDeclaration.getParameters().get(0)
            );
            J.NewClass newClassDeclaration = JavaTemplate.builder(template)
                    .contextSensitive()
                    .imports(BI_CONSUMER)
                    .build()
                    .apply(updateCursor(nc), nc.getCoordinates().replace(),
                            successCallbackMethodDeclaration.getBody(),
                            failureCallbackMethodDeclaration.getBody());

            List<Statement> additionalStatements = ListUtils.map(nc.getBody().getStatements(), s ->
                    s == successCallbackMethodDeclaration || s == failureCallbackMethodDeclaration ? null : s);
            Statement acceptMethodWithPrefix = newClassDeclaration.getBody().getStatements().get(0)
                    .withPrefix(successCallbackMethodDeclaration.getPrefix());
            newClassDeclaration = newClassDeclaration.withBody(nc.getBody().
                    withStatements(ListUtils.concat(additionalStatements, acceptMethodWithPrefix)));

            maybeAddImport(BI_CONSUMER);
            return newClassDeclaration.withPrefix(nc.getPrefix());
        }

        String template = String.format("(%s, %s) -> {\n" +
                                        "    if (ex == null) #{}\n" +
                                        "    else #{}\n" +
                                        "}",
                successCallbackMethodDeclaration.getParameters().get(0),
                failureCallbackMethodDeclaration.getParameters().get(0)
        );
        J.Lambda newClassDeclaration = JavaTemplate.builder(template)
                .contextSensitive()
                .imports(BI_CONSUMER)
                .build()
                .apply(updateCursor(nc), nc.getCoordinates().replace(),
                        successCallbackMethodDeclaration.getBody(),
                        failureCallbackMethodDeclaration.getBody());
        return newClassDeclaration.withPrefix(nc.getPrefix());
    }

    @SuppressWarnings("DataFlowIssue")
    private static J.@Nullable MethodDeclaration extract(MethodMatcher matcher, J j) {
        AtomicReference<J.MethodDeclaration> methodDecl = new AtomicReference<>();
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (matcher.matches(method.getMethodType())) {
                    methodDecl.set(method);
                }
                return super.visitMethodDeclaration(method, ctx);
            }
        }.visitNonNull(j, new InMemoryExecutionContext());
        return methodDecl.get();
    }
}

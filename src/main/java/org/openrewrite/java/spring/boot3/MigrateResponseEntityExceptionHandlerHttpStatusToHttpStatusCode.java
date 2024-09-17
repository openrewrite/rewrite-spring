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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singletonList;

public class MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode extends Recipe {

    private static final String HTTP_STATUS_FQ = "org.springframework.http.HttpStatus";
    private static final String HTTP_STATUS_CODE_FQ = "org.springframework.http.HttpStatusCode";
    private static final String RESPONSE_ENTITY_EXCEPTION_HANDLER_FQ = "org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler";

    @Override
    public String getDisplayName() {
        return "Migrate `ResponseEntityExceptionHandler` from HttpStatus to HttpStatusCode";
    }

    @Override
    public String getDescription() {
        return "With Spring 6 `HttpStatus` was replaced by `HttpStatusCode` in most method signatures in the `ResponseEntityExceptionHandler`.";
    }


    private static final MethodMatcher HANDLER_METHOD = new MethodMatcher(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler *(..)", true);

    private static final MethodMatcher HTTP_STATUS_VALUE_OF = new MethodMatcher(
            HTTP_STATUS_FQ + " valueOf(java.lang.int)", true);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(HANDLER_METHOD),
                new JavaIsoVisitor<ExecutionContext>() {

                    // TODO remove debug
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                        System.out.println(TreeVisitingPrinter.printTree(cd));
                        return cd;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = method;
                        if (HANDLER_METHOD.matches(m.getMethodType()) && hasHttpStatusParameter(m)) {
                            final JavaType.Method met = m.getMethodType().withParameterTypes(ListUtils.map(m.getMethodType().getParameterTypes(), type -> {
                                if (TypeUtils.isAssignableTo(HTTP_STATUS_FQ, type)) {
                                    return JavaType.buildType(HTTP_STATUS_CODE_FQ);
                                }
                                return type;
                            }));

                            m = m.withMethodType(met);
                            m = m.withParameters(ListUtils.map(m.getParameters(), var -> {
                                if (var instanceof J.VariableDeclarations) {
                                    J.VariableDeclarations v = (J.VariableDeclarations) var;
                                    J.VariableDeclarations.NamedVariable declaredVar = v.getVariables().get(0);
                                    declaredVar = declaredVar.withVariableType(declaredVar.getVariableType().withOwner(met));
                                    v = v.withVariables(singletonList(declaredVar));
                                    if (TypeUtils.isOfType(v.getType(), JavaType.buildType(HTTP_STATUS_FQ))) {
                                        String httpStatusCodeSimpleName = HTTP_STATUS_CODE_FQ.substring(HTTP_STATUS_CODE_FQ.lastIndexOf("."));
                                        v = v.withTypeExpression(TypeTree.build(httpStatusCodeSimpleName)
                                                .withType(JavaType.buildType(HTTP_STATUS_CODE_FQ)));
                                        v = v.withVariables(singletonList(declaredVar.withType(JavaType.buildType(HTTP_STATUS_CODE_FQ))));
                                        return v;
                                    }
                                }
                                return var;
                            }));
//                            updateCursor(m);
                        }
                        maybeAddImport(HTTP_STATUS_CODE_FQ);
                        maybeRemoveImport(HTTP_STATUS_FQ);
                        return super.visitMethodDeclaration(m, ctx);
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                        if (TypeUtils.isAssignableTo(HTTP_STATUS_FQ, vd.getType())) {
                            if (vd.getVariables().get(0).getInitializer() instanceof J.MethodInvocation) {
                                J.MethodInvocation init = (J.MethodInvocation) vd.getVariables().get(0).getInitializer();
                                if (init != null && !HTTP_STATUS_VALUE_OF.matches(init.getMethodType())) {
                                    vd = JavaTemplate.builder("HttpStatus.valueOf(#{any()})")
                                            .imports(HTTP_STATUS_FQ)
                                            .contextSensitive()
                                            .doBeforeParseTemplate(System.out::println)
                                            .build()
                                            .apply(getCursor(), init.getCoordinates().replace(), init);
                                }

                            };
                        }
                        return vd;
                    }

                    @Override
                    public J.Return visitReturn(J.Return _return, ExecutionContext ctx) {
                        J.Return r = super.visitReturn(_return, ctx);
                        J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        if (r.getExpression() instanceof J.MethodInvocation) {
                            J.MethodInvocation returnMethod = (J.MethodInvocation) r.getExpression();
                            if (returnMethod.getSelect() instanceof J.Identifier) {
                                J.Identifier returnSelect = (J.Identifier)returnMethod.getSelect();
                                if (returnSelect.getSimpleName().equals("super") && TypeUtils.isAssignableTo(RESPONSE_ENTITY_EXCEPTION_HANDLER_FQ, returnMethod.getMethodType().getDeclaringType())) {
                                    ((J.VariableDeclarations)method.getParameters().get(0)).getVariables().get(0).getName();
                                    List<Expression> expressions = new ArrayList<>();
                                    for (Statement stmt : method.getParameters()) {
                                        expressions.add(((J.VariableDeclarations)stmt).getVariables().get(0).getName());
                                    }
                                    return maybeAutoFormat(r.withExpression((((J.MethodInvocation) r.getExpression()).withArguments(expressions))), _return, ctx);
                                }
                            }
                        }
                        return super.visitReturn(_return, ctx);
                    }

                    private boolean hasHttpStatusParameter(J.MethodDeclaration m) {
                        return m.getParameters().stream().anyMatch(p ->
                                p instanceof J.VariableDeclarations &&
                                TypeUtils.isAssignableTo(HTTP_STATUS_FQ, ((J.VariableDeclarations) p).getType()));
                    }
                }
        );
    }
}

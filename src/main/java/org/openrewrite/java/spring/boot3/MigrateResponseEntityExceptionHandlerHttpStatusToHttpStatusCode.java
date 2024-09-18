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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

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
            HTTP_STATUS_FQ + " valueOf(int)", true);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(HANDLER_METHOD),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = method;
                        if (m.getMethodType() != null && HANDLER_METHOD.matches(m.getMethodType()) && hasHttpStatusParameter(m)) {
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
                                    if (declaredVar.getVariableType() != null) {
                                        declaredVar = declaredVar
                                                .withVariableType(declaredVar
                                                        .getVariableType()
                                                        .withOwner(met))
                                                .withName(declaredVar
                                                        .getName()
                                                        .withType(JavaType.buildType(HTTP_STATUS_CODE_FQ)));
                                                if (declaredVar.getName().getFieldType() != null) {
                                                    declaredVar = declaredVar.withName(declaredVar.getName()
                                                            .withFieldType(declaredVar
                                                                    .getName()
                                                                    .getFieldType()
                                                                    .withType(JavaType.buildType(HTTP_STATUS_CODE_FQ)))
                                                    );
                                                }
                                        v = v.withVariables(singletonList(declaredVar));
                                        if (TypeUtils.isOfType(v.getType(), JavaType.buildType(HTTP_STATUS_FQ))) {
                                            String httpStatusCodeSimpleName = HTTP_STATUS_CODE_FQ.substring(HTTP_STATUS_CODE_FQ.lastIndexOf("."));
                                            v = v.withTypeExpression(TypeTree.build(httpStatusCodeSimpleName)
                                                    .withType(JavaType.buildType(HTTP_STATUS_CODE_FQ)));
                                            v = v.withVariables(singletonList(declaredVar.withType(JavaType.buildType(HTTP_STATUS_CODE_FQ))));
                                            return v;
                                        }
                                    }
                                }
                                return var;
                            }));
                        }
                        updateCursor(m);
                        maybeAddImport(HTTP_STATUS_CODE_FQ);
                        maybeRemoveImport(HTTP_STATUS_FQ);
                        return super.visitMethodDeclaration(m, ctx);
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                        if (TypeUtils.isAssignableTo(HTTP_STATUS_FQ, vd.getType())) {
                            vd = vd.withVariables(ListUtils.map(vd.getVariables(), var -> {
                                if (var.getInitializer() instanceof J.MethodInvocation) {
                                    J.MethodInvocation init = (J.MethodInvocation) var.getInitializer();
                                    if (!HTTP_STATUS_VALUE_OF.matches(init.getMethodType())) {
                                        J.MethodInvocation apply = JavaTemplate.builder("HttpStatus.valueOf(#{any(int)})")
                                                .imports(HTTP_STATUS_FQ)
                                                .javaParser(JavaParser.fromJavaVersion().classpath("spring-web"))
                                                .build()
                                                .apply(new Cursor(getCursor(), init), init.getCoordinates().replace(), init);
                                        return var.withInitializer(apply);
                                    }
                                }
                                return var;
                            }));
                        }
                        return vd;
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext executionContext) {
                        J.Identifier ident = super.visitIdentifier(identifier, executionContext);
                        J.MethodDeclaration methodScope = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        if (methodScope != null) {
                            for (Statement stmt : methodScope.getParameters()) {
                                if (stmt instanceof J.VariableDeclarations) {
                                    J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                                    for (J.VariableDeclarations.NamedVariable var : vd.getVariables()) {
                                        if (var.getName().getSimpleName().equals(ident.getSimpleName())) {
                                            if (!TypeUtils.isOfType(var.getName().getType(), ident.getType())) {
                                                ident = ident.withType(var.getName().getType());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        return super.visitIdentifier(ident, executionContext);
                    }

                    @Override
                    public J.Return visitReturn(J.Return _return, ExecutionContext ctx) {
                        J.Return r = super.visitReturn(_return, ctx);
                        J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        if (r.getExpression() instanceof J.MethodInvocation) {
                            J.MethodInvocation returnMethod = (J.MethodInvocation) r.getExpression();
                            if (returnMethod.getSelect() instanceof J.Identifier) {
                                J.Identifier returnSelect = (J.Identifier) returnMethod.getSelect();
                                if (returnMethod.getMethodType() != null) {
                                    if (method != null && returnSelect.getSimpleName().equals("super") && TypeUtils.isAssignableTo(RESPONSE_ENTITY_EXCEPTION_HANDLER_FQ, returnMethod.getMethodType().getDeclaringType())) {
                                        List<Expression> expressions = new ArrayList<>();
                                        for (Statement stmt : method.getParameters()) {
                                            expressions.add(((J.VariableDeclarations) stmt).getVariables().get(0).getName());
                                        }
                                        return maybeAutoFormat(_return, r.withExpression((((J.MethodInvocation) r.getExpression()).withArguments(expressions))), ctx);
                                    }
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

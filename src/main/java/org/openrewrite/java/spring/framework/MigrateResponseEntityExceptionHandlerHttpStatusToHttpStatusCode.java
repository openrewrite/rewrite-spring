/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.framework;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode extends Recipe {

    private static final String HTTP_STATUS_FQ = "org.springframework.http.HttpStatus";
    private static final String HTTP_STATUS_CODE_FQ = "org.springframework.http.HttpStatusCode";
    private static final String RESPONSE_ENTITY_EXCEPTION_HANDLER_FQ = "org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler";
    private static final MethodMatcher HANDLER_METHOD = new MethodMatcher(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler *(..)", true);

    @Getter
    final String displayName = "Migrate `ResponseEntityExceptionHandler` from HttpStatus to HttpStatusCode";

    @Getter
    final String description = "With Spring 6 `HttpStatus` was replaced by `HttpStatusCode` in most method signatures in the `ResponseEntityExceptionHandler`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(RESPONSE_ENTITY_EXCEPTION_HANDLER_FQ, true),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = method;
                        if (HANDLER_METHOD.matches(m.getMethodType())) {
                            JavaType javaTypeHttpStatusCode = JavaType.buildType(HTTP_STATUS_CODE_FQ);
                            //noinspection DataFlowIssue
                            JavaType.Method met = m.getMethodType().withParameterTypes(ListUtils.map(m.getMethodType().getParameterTypes(),
                                    type -> TypeUtils.isAssignableTo(HTTP_STATUS_FQ, type) ? javaTypeHttpStatusCode : type));
                            if (met == m.getMethodType()) {
                                // There was no parameter to change
                                return m;
                            }

                            m = m.withMethodType(met);
                            m = m.withParameters(ListUtils.map(m.getParameters(), var -> {
                                if (var instanceof J.VariableDeclarations) {
                                    J.VariableDeclarations v = (J.VariableDeclarations) var;
                                    J.VariableDeclarations.NamedVariable declaredVar = v.getVariables().get(0);
                                    if (declaredVar.getVariableType() != null && TypeUtils.isAssignableTo(HTTP_STATUS_FQ, v.getType())) {
                                        J.Identifier newName = declaredVar.getName().withType(javaTypeHttpStatusCode);
                                        if (newName.getFieldType() != null) {
                                            newName = newName.withFieldType(newName.getFieldType().withType(javaTypeHttpStatusCode));
                                        }
                                        declaredVar = declaredVar
                                                .withName(newName)
                                                .withVariableType(declaredVar.getVariableType().withOwner(met));
                                        return v.withVariables(singletonList(declaredVar.withType(javaTypeHttpStatusCode)))
                                                .withTypeExpression(TypeTree.build("HttpStatusCode")
                                                        .withType(javaTypeHttpStatusCode)
                                                        .withPrefix(requireNonNull(v.getTypeExpression()).getPrefix()));
                                    }
                                }
                                return var;
                            }));
                        }
                        updateCursor(m);
                        maybeRemoveImport(HTTP_STATUS_FQ);
                        maybeAddImport(HTTP_STATUS_CODE_FQ);
                        return super.visitMethodDeclaration(m, ctx);
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                        J.Identifier ident = super.visitIdentifier(identifier, ctx);
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
                        return super.visitIdentifier(ident, ctx);
                    }
                }
        );
    }
}

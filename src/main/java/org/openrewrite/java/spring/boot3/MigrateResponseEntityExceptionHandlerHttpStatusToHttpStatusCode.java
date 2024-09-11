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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Collections.singletonList;

public class MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode extends Recipe {

    private static final String HTTP_STATUS_FQ = "org.springframework.http.HttpStatus";
    private static final String HTTP_STATUS_CODE_FQ = "org.springframework.http.HttpStatusCode";

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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(HANDLER_METHOD),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
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
                        }
                        maybeAddImport(HTTP_STATUS_CODE_FQ);
                        maybeRemoveImport(HTTP_STATUS_FQ);
                        return m;
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

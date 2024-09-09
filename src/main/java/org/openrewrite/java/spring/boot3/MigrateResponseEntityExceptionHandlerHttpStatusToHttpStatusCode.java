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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.List;

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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
                        List<Statement> vars = m.getParameters();
                        for (int i = 0; i < vars.size(); i++) {
                            Statement var = vars.get(i);
                            if (var instanceof J.VariableDeclarations) {
                                J.VariableDeclarations v = (J.VariableDeclarations) var;
                                if (TypeUtils.isOfType(v.getType(), JavaType.buildType(HTTP_STATUS_FQ))) {
                                    String httpStatusCodeSimpleName = HTTP_STATUS_CODE_FQ.substring(HTTP_STATUS_CODE_FQ.lastIndexOf("."));
                                    J.VariableDeclarations.NamedVariable declaredVar = v.getVariables().get(0);
                                    v = v.withTypeExpression(TypeTree.build(httpStatusCodeSimpleName)
                                                    .withType(JavaType.buildType(HTTP_STATUS_CODE_FQ)))
                                            .withVariables(singletonList(declaredVar.withType(JavaType.buildType(HTTP_STATUS_CODE_FQ))));
                                    vars.set(i, v);
                                    maybeAddImport(HTTP_STATUS_CODE_FQ);
                                    maybeRemoveImport(HTTP_STATUS_FQ);
                                }
                            }
                        }
                        return m.withParameters(vars);
                    }
                }
        );
    }
}

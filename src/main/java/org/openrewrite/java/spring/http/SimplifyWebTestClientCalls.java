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
package org.openrewrite.java.spring.http;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;

import static java.util.Collections.emptyList;

public class SimplifyWebTestClientCalls extends Recipe {

    private static final MethodMatcher IS_EQUAL_TO_MATCHER = new MethodMatcher("org.springframework.test.web.reactive.server.StatusAssertions isEqualTo(..)");

    @Override
    public String getDisplayName() {
        return "Simplify WebTestClient expressions";
    }

    @Override
    public String getDescription() {
        return "Simplifies various types of WebTestClient expressions to improve code readability.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(IS_EQUAL_TO_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (IS_EQUAL_TO_MATCHER.matches(m.getMethodType())) {
                    final int statusCode = extractStatusCode(m.getArguments().get(0));
                    switch (statusCode) {
                        case 200:
                            return replaceMethod(m, "isOk()");
                        case 201:
                            return replaceMethod(m, "isCreated()");
                        case 202:
                            return replaceMethod(m, "isAccepted()");
                        case 204:
                            return replaceMethod(m, "isNoContent()");
                        case 302:
                            return replaceMethod(m, "isFound()");
                        case 303:
                            return replaceMethod(m, "isSeeOther()");
                        case 304:
                            return replaceMethod(m, "isNotModified()");
                        case 307:
                            return replaceMethod(m, "isTemporaryRedirect()");
                        case 308:
                            return replaceMethod(m, "isPermanentRedirect()");
                        case 400:
                            return replaceMethod(m, "isBadRequest()");
                        case 401:
                            return replaceMethod(m, "isUnauthorized()");
                        case 403:
                            return replaceMethod(m, "isForbidden()");
                        case 404:
                            return replaceMethod(m, "isNotFound()");
                    }
                }
                return m;
            }

            private int extractStatusCode(Expression expression) {
                if (expression instanceof J.FieldAccess) {
                    //isEqualTo(HttpStatus.OK)
                    J.FieldAccess fa = (J.FieldAccess) expression;
                    if (fa.getTarget() instanceof J.Identifier) {
                        if ("HttpStatus".equals(((J.Identifier) fa.getTarget()).getSimpleName())) {
                            switch (fa.getSimpleName()) {
                                case "OK":
                                    return 200;
                                case "CREATED":
                                    return 201;
                                case "ACCEPTED":
                                    return 202;
                                case "NO_CONTENT":
                                    return 204;
                                case "FOUND":
                                    return 302;
                                case "SEE_OTHER":
                                    return 303;
                                case "NOT_MODIFIED":
                                    return 304;
                                case "TEMPORARY_REDIRECT":
                                    return 307;
                                case "PERMANENT_REDIRECT":
                                    return 308;
                                case "BAD_REQUEST":
                                    return 400;
                                case "UNAUTHORIZED":
                                    return 401;
                                case "FORBIDDEN":
                                    return 403;
                                case "NOT_FOUND":
                                    return 404;
                            }
                        }
                    }
                } else if (expression instanceof J.Literal) {
                    //isEqualTo(200)
                    Object raw = ((J.Literal) expression).getValue();
                    if (raw instanceof Integer) {
                        return (int) raw;
                    }
                } else if (expression instanceof J.MethodInvocation) {
                    //isEqualTo(HttpStatus.valueOf(200))
                    //isEqualTo(HttpStatusCode.valueOf(200))
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) expression;
                    List<Expression> arguments = methodInvocation.getArguments();
                    if (arguments.size() == 1 && arguments.get(0) instanceof J.Literal) {
                        Object raw = ((J.Literal) arguments.get(0)).getValue();
                        if (raw instanceof Integer) {
                            return (int) raw;
                        }
                    }
                }
                return -1;
            }

            private J.MethodInvocation replaceMethod(J.MethodInvocation method, String methodName) {
                maybeRemoveImport("org.springframework.http.HttpStatus");
                maybeRemoveImport("org.springframework.http.HttpStatusCode");
                J.MethodInvocation methodInvocation = JavaTemplate.apply(methodName, getCursor(), method.getCoordinates().replaceMethod());
                JavaType.Method type = methodInvocation
                        .getMethodType()
                        .withParameterNames(emptyList())
                        .withParameterTypes(emptyList());
                return methodInvocation
                        .withArguments(emptyList())
                        .withMethodType(type)
                        .withName(methodInvocation.getName().withType(type));

            }
        });
    }
}


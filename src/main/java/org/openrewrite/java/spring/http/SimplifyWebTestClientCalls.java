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
import org.openrewrite.java.tree.J.Literal;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.TypeUtils;

public class SimplifyWebTestClientCalls extends Recipe {

    private static final String IS_EQUAL_TO =
            "org.springframework.test.web.reactive.server.StatusAssertions isEqualTo(int)";

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
        return Preconditions.check(new UsesMethod<>(IS_EQUAL_TO),
                new SimplifyWebTestClientVisitor());
    }

    private final class SimplifyWebTestClientVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher isEqualToMatcher = new MethodMatcher(IS_EQUAL_TO);

        @Override
        public MethodInvocation visitMethodInvocation(MethodInvocation method,
                ExecutionContext ctx) {
            if (!isEqualToMatcher.matches(method.getMethodType())) {
                return method;
            }
            Expression argument = method.getArguments().get(0);
            if (TypeUtils.isOfClassType(argument.getType(), "int")) {
                int statusCode = (int) ((Literal) argument).getValue();
                switch (statusCode) {
                    case 200:
                        return replaceMethod(method, "isOk()");
                    case 201:
                        return replaceMethod(method, "isCreated()");
                    case 202:
                        return replaceMethod(method, "isAccepted()");
                    case 204:
                        return replaceMethod(method, "isNoContent()");
                    case 302:
                        return replaceMethod(method, "isFound()");
                    case 303:
                        return replaceMethod(method, "isSeeOther()");
                    case 304:
                        return replaceMethod(method, "isNotModified()");
                    case 307:
                        return replaceMethod(method, "isTemporaryRedirect()");
                    case 308:
                        return replaceMethod(method, "isPermanentRedirect()");
                    case 400:
                        return replaceMethod(method, "isBadRequest()");
                    case 401:
                        return replaceMethod(method, "isUnauthorized()");
                    case 403:
                        return replaceMethod(method, "isForbidden()");
                    case 404:
                        return replaceMethod(method, "isNotFound()");
                    default:
                        return method;
                }
            }
            return super.visitMethodInvocation(method, ctx);
        }

        private MethodInvocation replaceMethod(MethodInvocation method, String methodName) {
            JavaTemplate template = JavaTemplate.builder(methodName).build();
            return template.apply(getCursor(), method.getCoordinates().replaceMethod());
        }
    }
}

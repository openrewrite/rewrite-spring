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
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J.Literal;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.TypeUtils;

public class SimplifyWebTestClientCalls extends Recipe {

    @Override
    public String getDisplayName() {
        return "Simplify WebTestClient expressions";
    }

    @Override
    public String getDescription() {
        return "Simplifies various types of WebTestClient expressions to improve code readability.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            private static final String HTTP_STATUS = "org.springframework.http.HttpStatus";
            private static final String INT = "int";
            private final MethodMatcher isEqualToMatcher
                    = new MethodMatcher("org.springframework.test.web.reactive.server.StatusAssertions isEqualTo(..)");
            private final JavaTemplate isOkTemplate
                    = JavaTemplate.builder("isOk()").build();

            @Override
            public MethodInvocation visitMethodInvocation(MethodInvocation method, ExecutionContext ctx) {
                if (!isEqualToMatcher.matches(method.getMethodType())) {
                    return method;
                }
                Expression argument = method.getArguments().get(0);
                if (TypeUtils.isOfClassType(argument.getType(), INT)) {
                    return replaceInt(method, argument);
                } else if (TypeUtils.isOfClassType(argument.getType(), HTTP_STATUS)) {
                    return replaceHttpStatus(method, argument);
                }
                return super.visitMethodInvocation(method, ctx);
            }

            private MethodInvocation replaceInt(MethodInvocation method, Expression expression) {
                if ((int) ((Literal) expression).getValue() == 200) {
                    return isOkTemplate.apply(getCursor(), method.getCoordinates().replaceMethod());
                }
                return method;
            }

            private MethodInvocation replaceHttpStatus(MethodInvocation method, Expression expression) {
                // TODO: Check if value of HttpStatus == 200
                if (true) {
                    MethodInvocation methodInvocation = isOkTemplate.apply(getCursor(), method.getCoordinates().replaceMethod());
                    maybeRemoveImport(HTTP_STATUS);
                    return methodInvocation;
                }
                return method;
            }
        };
    }
}

/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.security6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Objects;

public class PropagateAuthenticationServiceExceptions extends Recipe {

    private static final MethodMatcher MATCHER = new MethodMatcher("org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler setRethrowAuthenticationServiceException(boolean)");

    @Override
    public String getDisplayName() {
        return "Remove calls matching `AuthenticationEntryPointFailureHandler.setRethrowAuthenticationServiceException(true)`";
    }

    @Override
    public String getDescription() {
        return "Remove any calls matching `AuthenticationEntryPointFailureHandler.setRethrowAuthenticationServiceException(true)`. "
                + "See the corresponding [Sprint Security 6.0 migration step](https://docs.spring.io/spring-security/reference/6.0.0/migration/servlet/authentication.html#_propagate_authenticationserviceexceptions) for details.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);
                if (method.getSelect() != null && method.getArguments().size() == 1 && MATCHER.matches(method)) {
                    Expression arg0 = method.getArguments().get(0);
                    if (arg0 instanceof J.Literal && Objects.equals(((J.Literal) arg0).getValue(), Boolean.TRUE)) {
                        //noinspection DataFlowIssue
                        return null;
                    }
                }
                return method;
            }
        });
    }
}

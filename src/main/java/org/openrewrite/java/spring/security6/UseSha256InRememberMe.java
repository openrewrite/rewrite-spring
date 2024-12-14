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
package org.openrewrite.java.spring.security6;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.internal.LocalVariableUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;

public class UseSha256InRememberMe extends Recipe {

    private static final JavaType.Class REMEMBER_ME_TOKEN_ALGORITHM_TYPE = (JavaType.Class) JavaType.buildType("org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices$RememberMeTokenAlgorithm");
    private static final MethodMatcher REMEMBER_ME_SERVICES_CONSTRUCTOR_MATCHER =
            new MethodMatcher("org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices <constructor>(String, org.springframework.security.core.userdetails.UserDetailsService, " +
                              REMEMBER_ME_TOKEN_ALGORITHM_TYPE.getFullyQualifiedName() + ")");
    private static final MethodMatcher REMEMBER_ME_SERVICES_SET_MATCHING_ALGORITHM_MATCHER =
            new MethodMatcher("org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices setMatchingAlgorithm(" +
                              REMEMBER_ME_TOKEN_ALGORITHM_TYPE.getFullyQualifiedName() + ")");

    @Override
    public String getDisplayName() {
        return "Remove explicit configuration of SHA-256 as encoding and matching algorithm for `TokenBasedRememberMeServices`";
    }

    @Override
    public String getDescription() {
        return "As of Spring Security 6.0 the SHA-256 algorithm is the default for the encoding and matching algorithm used by `TokenBasedRememberMeServices` and does thus no longer need to be explicitly configured. " +
               "See the corresponding [Sprint Security 6.0 migration step](https://docs.spring.io/spring-security/reference/6.0.0/migration/servlet/authentication.html#servlet-opt-in-sha256-rememberme) for details.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                newClass = super.visitNewClass(newClass, ctx);
                if (newClass.getArguments().size() == 3 && REMEMBER_ME_SERVICES_CONSTRUCTOR_MATCHER.matches(newClass)) {
                    if (isSha256Algorithm(newClass.getArguments().get(2), getCursor())) {
                        return newClass.withArguments(new ArrayList<>(newClass.getArguments().subList(0, 2)));
                    }
                }
                return newClass;
            }

            @Override
            @SuppressWarnings("DataFlowIssue")
            public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);
                if (method.getSelect() != null && method.getArguments().size() == 1 &&
                    REMEMBER_ME_SERVICES_SET_MATCHING_ALGORITHM_MATCHER.matches(method)) {
                    if (isSha256Algorithm(method.getArguments().get(0), getCursor())) {
                        return null;
                    }
                }
                return method;
            }
        });
    }

    private boolean isSha256Algorithm(Expression expression, Cursor cursor) {
        Expression resolvedExpression = LocalVariableUtils.resolveExpression(expression, cursor);
        JavaType.Variable fieldType = null;
        if (resolvedExpression instanceof J.Identifier) {
            fieldType = ((J.Identifier) resolvedExpression).getFieldType();
        } else if (resolvedExpression instanceof J.FieldAccess) {
            fieldType = ((J.FieldAccess) resolvedExpression).getName().getFieldType();
        }

        return fieldType != null && "SHA256".equals(fieldType.getName()) && TypeUtils.isOfType(fieldType.getType(), REMEMBER_ME_TOKEN_ALGORITHM_TYPE);
    }

}

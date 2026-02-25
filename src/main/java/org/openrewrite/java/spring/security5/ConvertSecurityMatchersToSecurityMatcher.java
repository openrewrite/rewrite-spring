/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.security5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

@EqualsAndHashCode(callSuper = false)
@Value
public class ConvertSecurityMatchersToSecurityMatcher extends Recipe {

    private static final String HTTP_SECURITY = "org.springframework.security.config.annotation.web.builders.HttpSecurity";
    private static final String ABSTRACT_REGISTRY = "org.springframework.security.config.annotation.web.AbstractRequestMatcherRegistry";

    private static final MethodMatcher REQUEST_MATCHERS = new MethodMatcher(HTTP_SECURITY + " requestMatchers()");
    private static final MethodMatcher ANT_MATCHERS = new MethodMatcher(ABSTRACT_REGISTRY + " antMatchers(..)");
    private static final MethodMatcher MVC_MATCHERS = new MethodMatcher(ABSTRACT_REGISTRY + " mvcMatchers(..)", true);
    private static final MethodMatcher REGEX_MATCHERS = new MethodMatcher(ABSTRACT_REGISTRY + " regexMatchers(..)");

    String displayName = "Convert `requestMatchers` chain to `securityMatcher`";

    String description = "Converts `HttpSecurity.requestMatchers().antMatchers(...)` and similar patterns to " +
            "`HttpSecurity.securityMatcher(...)`. The no-arg `requestMatchers()` method returns a `RequestMatcherConfigurer` " +
            "that is not a configurer in the lambda DSL sense, so it should be replaced with the `securityMatcher()` method " +
            "introduced in Spring Security 5.8.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(HTTP_SECURITY, true), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                // Match antMatchers/mvcMatchers/regexMatchers whose select is requestMatchers() on HttpSecurity
                if ((ANT_MATCHERS.matches(mi) || MVC_MATCHERS.matches(mi) || REGEX_MATCHERS.matches(mi)) &&
                        mi.getSelect() instanceof J.MethodInvocation) {
                    J.MethodInvocation selectMi = (J.MethodInvocation) mi.getSelect();
                    if (REQUEST_MATCHERS.matches(selectMi) && selectMi.getSelect() != null) {
                        // Build method type for securityMatcher on HttpSecurity
                        JavaType.Method newMethodType = null;
                        if (mi.getMethodType() != null && selectMi.getMethodType() != null) {
                            JavaType.FullyQualified httpSecurityType = TypeUtils.asFullyQualified(
                                    selectMi.getMethodType().getDeclaringType());
                            if (httpSecurityType != null) {
                                newMethodType = mi.getMethodType()
                                        .withName("securityMatcher")
                                        .withDeclaringType(httpSecurityType)
                                        .withReturnType(httpSecurityType);
                            }
                        }

                        // Use selectMi (requestMatchers) as base to preserve its formatting position,
                        // then replace method name and arguments from the outer matcher call
                        J.MethodInvocation result = selectMi
                                .withPrefix(mi.getPrefix())
                                .withName(selectMi.getName().withSimpleName("securityMatcher").withType(newMethodType))
                                .withMethodType(newMethodType);
                        return result.getPadding().withArguments(mi.getPadding().getArguments());
                    }
                }

                // Remove redundant .and() after securityMatcher transformation;
                // securityMatcher() already returns HttpSecurity, so .and() is a no-op
                if ("and".equals(mi.getSimpleName()) && mi.getSelect() instanceof J.MethodInvocation) {
                    J.MethodInvocation selectMi = (J.MethodInvocation) mi.getSelect();
                    if ("securityMatcher".equals(selectMi.getSimpleName())) {
                        return selectMi.withPrefix(mi.getPrefix());
                    }
                }

                return mi;
            }
        });
    }
}

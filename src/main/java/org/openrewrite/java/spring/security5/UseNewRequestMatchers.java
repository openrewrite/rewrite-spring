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
package org.openrewrite.java.spring.security5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Applicability;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = false)
public class UseNewRequestMatchers extends Recipe {

    private static final MethodMatcher ANT_MATCHERS = new MethodMatcher("org.springframework.security.config.annotation.web.AbstractRequestMatcherRegistry antMatchers(..)");
    private static final MethodMatcher MVC_MATCHERS = new MethodMatcher("org.springframework.security.config.annotation.web.AbstractRequestMatcherRegistry mvcMatchers(..)", true);
    private static final MethodMatcher REGEX_MATCHERS = new MethodMatcher("org.springframework.security.config.annotation.web.AbstractRequestMatcherRegistry regexMatchers(..)");


    @NonNull
    @Override
    public String getDisplayName() {
        return "Use the new `requestMatchers` methods";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "In Spring Security 5.8, the `antMatchers`, `mvcMatchers`, and `regexMatchers` methods were deprecated in favor of new `requestMatchers` methods. Refer to the [Spring Security docs](https://docs.spring.io/spring-security/reference/5.8/migration/servlet/config.html#use-new-requestmatchers) for more information.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return Applicability.or(
                new UsesMethod<>(ANT_MATCHERS),
                new UsesMethod<>(MVC_MATCHERS),
                new UsesMethod<>(REGEX_MATCHERS));
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (ANT_MATCHERS.matches(mi) || MVC_MATCHERS.matches(mi) || REGEX_MATCHERS.matches(mi)) {
                    mi = maybeChangeMethodInvocation(mi);
                }
                return mi;
            }
        };

    }

    private J.MethodInvocation maybeChangeMethodInvocation(J.MethodInvocation mi) {
        JavaType.Method requestMatchersMethod = findRequestMatchersMethodWithMatchingParameterTypes(mi);
        if (requestMatchersMethod != null) {
            return mi
                    .withMethodType(requestMatchersMethod)
                    .withName(mi.getName().withSimpleName("requestMatchers"));
        } else {
            return mi;
        }
    }

    @Nullable
    private JavaType.Method findRequestMatchersMethodWithMatchingParameterTypes(J.MethodInvocation mi) {
        JavaType.Method methodType = requireNonNull(mi.getMethodType(), "methodType");
        List<JavaType> parameterTypes = methodType.getParameterTypes();
        List<JavaType.Method> methods;
        boolean isOverride = TypeUtils.isOverride(mi.getMethodType());
        if (isOverride) {
            methods = requireNonNull(methodType.getDeclaringType().getSupertype(), "superType").getMethods();
        } else {
            methods = methodType.getDeclaringType().getMethods();
        }
        return methods.stream()
                .filter(m -> m.getName().equals("requestMatchers"))
                .filter(m -> m.getParameterTypes().equals(parameterTypes))
                .findFirst()
                .orElse(null);
    }
}

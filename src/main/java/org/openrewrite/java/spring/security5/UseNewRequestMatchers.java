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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class UseNewRequestMatchers extends Recipe {

    private static final MethodMatcher ANT_MATCHERS_HTTP_METHOD = new MethodMatcher("org.springframework.security.config.annotation.web.AbstractRequestMatcherRegistry antMatchers(org.springframework.http.HttpMethod)");
    private static final MethodMatcher ANT_MATCHERS_ANT_PATTERNS = new MethodMatcher("org.springframework.security.config.annotation.web.AbstractRequestMatcherRegistry antMatchers(java.lang.String[])");
    private static final MethodMatcher ANT_MATCHERS_HTTP_METHOD_ANT_PATTERNS = new MethodMatcher("org.springframework.security.config.annotation.web.AbstractRequestMatcherRegistry antMatchers(org.springframework.http.HttpMethod, java.lang.String[])");

    @Override
    public String getDisplayName() {
        return "Use the new `requestMatchers` methods";
    }

    @Override
    public String getDescription() {
        return "In Spring Security 5.8, the `antMatchers`, `mvcMatchers`, and `regexMatchers` methods were deprecated in favor of new `requestMatchers` methods. Refer to the [Spring Security docs](https://docs.spring.io/spring-security/reference/5.8/migration/servlet/config.html#use-new-requestmatchers) for more information.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (ANT_MATCHERS_ANT_PATTERNS.matches(mi)) {
                    String antPatterns = "requestMatchers(#{any(java.lang.String)})";
                    JavaTemplate javaTemplate = JavaTemplate.builder(this::getCursor, antPatterns).build();
                    mi = mi.withTemplate(javaTemplate, mi.getCoordinates().replaceMethod(), mi.getArguments().get(0));
                }
                else if (ANT_MATCHERS_HTTP_METHOD.matches(mi)) {
                    String httpMethod = "requestMatchers(#{any(org.springframework.http.HttpMethod)})";
                    JavaTemplate javaTemplate = JavaTemplate.builder(this::getCursor, httpMethod).build();
                    mi = mi.withTemplate(javaTemplate, mi.getCoordinates().replaceMethod(), mi.getArguments().get(0));
                }
                else if (ANT_MATCHERS_HTTP_METHOD_ANT_PATTERNS.matches(mi)) {
                    String methodAndAntPatterns = "requestMatchers(#{any(org.springframework.http.HttpMethod)}, #{any(java.lang.String)})";
                    JavaTemplate javaTemplate = JavaTemplate.builder(this::getCursor, methodAndAntPatterns).build();
                    mi = mi.withTemplate(javaTemplate, mi.getCoordinates().replaceMethod(), mi.getArguments().toArray());
                }
                return mi;
            }
        };
    }
}

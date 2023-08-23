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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import static java.util.stream.Collectors.joining;

@Value
@EqualsAndHashCode(callSuper = false)
public class UseNewRequestMatchers extends Recipe {

    private static final String CLAZZ = "org.springframework.security.config.annotation.web.AbstractRequestMatcherRegistry";
    private static final MethodMatcher ANT_MATCHERS = new MethodMatcher(CLAZZ + " antMatchers(..)");
    private static final MethodMatcher MVC_MATCHERS = new MethodMatcher(CLAZZ + " mvcMatchers(..)", true);
    private static final MethodMatcher REGEX_MATCHERS = new MethodMatcher(CLAZZ + " regexMatchers(..)");
    private static final MethodMatcher CSRF_MATCHERS = new MethodMatcher("org.springframework.security.config.annotation.web.configurers.CsrfConfigurer ignoringAntMatchers(..)");


    @Override
    public String getDisplayName() {
        return "Use the new `requestMatchers` methods";
    }

    @Override
    public String getDescription() {
        return "In Spring Security 5.8, the `antMatchers`, `mvcMatchers`, and `regexMatchers` methods were deprecated " +
                "in favor of new `requestMatchers` methods. Refer to the [Spring Security docs](https://docs.spring.io/spring-security/reference/5.8/migration/servlet/config.html#use-new-requestmatchers) " +
                "for more information.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                        new UsesMethod<>(ANT_MATCHERS),
                        new UsesMethod<>(MVC_MATCHERS),
                        new UsesMethod<>(REGEX_MATCHERS)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        boolean isCsrfMatcher = CSRF_MATCHERS.matches(mi);
                        if ((ANT_MATCHERS.matches(mi) || MVC_MATCHERS.matches(mi) || REGEX_MATCHERS.matches(mi) || isCsrfMatcher)
                                && mi.getSelect() != null) {
                            String parametersTemplate = mi.getArguments().stream().map(arg -> "#{any()}").collect(joining(", "));
                            String replacementMethodName = isCsrfMatcher ? "ignoringRequestMatchers" : "requestMatchers";
                            JavaTemplate template = JavaTemplate.builder(String.format(replacementMethodName + "(%s)", parametersTemplate))
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-security-config-5.8"))
                                    .build();
                            J.MethodInvocation apply = template.apply(getCursor(), mi.getCoordinates().replaceMethod(), mi.getArguments().toArray());
                            return apply.withSelect(mi.getSelect())
                                    .withName(mi.getName().withSimpleName(replacementMethodName));
                        }
                        return mi;
                    }
                });
    }
}

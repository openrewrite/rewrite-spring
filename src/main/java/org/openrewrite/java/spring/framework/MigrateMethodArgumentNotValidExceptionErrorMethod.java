/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.framework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class MigrateMethodArgumentNotValidExceptionErrorMethod extends Recipe {

    private static final String TARGET_CLASS = "org.springframework.web.bind.MethodArgumentNotValidException";

    private static final MethodMatcher ERRORS_TO_STRING_LIST = new MethodMatcher(TARGET_CLASS +
            " errorsToStringList(java.util.List)");

    private static final MethodMatcher ERRORS_TO_STRING_LIST_WITH_LOCALE = new MethodMatcher(TARGET_CLASS +
            " errorsToStringList(java.util.List, org.springframework.context.MessageSource, java.util.Locale)");

    private static final MethodMatcher RESOLVE_ERROR_MESSAGES = new MethodMatcher(TARGET_CLASS +
            " resolveErrorMessages(org.springframework.context.MessageSource, java.util.Locale)");

    @Override
    public String getDisplayName() {
        return "Migrate `MethodArgumentNotValidException.errorsToStringList` and `resolveErrorMessages`";
    }

    @Override
    public String getDescription() {
        return "`org.springframework.web.bind.MethodArgumentNotValidException.errorsToStringList` and `resolveErrorMessages` method was deprecated, in favor of `BindErrorUtils`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(TARGET_CLASS + " *rrors*(..)", false), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ERRORS_TO_STRING_LIST.matches(m)) {
                    maybeAddImport("org.springframework.web.util.BindErrorUtils");
                    return JavaTemplate.builder("BindErrorUtils.resolve(#{any()}).values().stream().toList()")
                            .imports("org.springframework.web.util.BindErrorUtils")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-context-6.+", "spring-web-6.+"))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0));
                } else if (ERRORS_TO_STRING_LIST_WITH_LOCALE.matches(m)) {
                    maybeAddImport("org.springframework.web.util.BindErrorUtils");
                    Expression messageSourceArg = m.getArguments().get(1);
                    if (messageSourceArg instanceof J.Literal) { // null
                        return JavaTemplate.builder("BindErrorUtils.resolve(#{any()}).values().stream().toList()")
                                .imports("org.springframework.web.util.BindErrorUtils")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-context-6.+", "spring-web-6.+"))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0));
                    }
                    return JavaTemplate.builder("BindErrorUtils.resolve(#{any()}, #{any(org.springframework.context.MessageSource)}, #{any()}).values().stream().toList()")
                            .imports("org.springframework.web.util.BindErrorUtils")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-context-6.+", "spring-web-6.+"))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0), messageSourceArg, m.getArguments().get(2));
                } else if (RESOLVE_ERROR_MESSAGES.matches(m)) {
                    maybeAddImport("org.springframework.web.util.BindErrorUtils");
                    return JavaTemplate.builder("BindErrorUtils.resolve(#{any()}.getAllErrors(), #{any()}, #{any()})")
                            .imports("org.springframework.web.util.BindErrorUtils")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-context-6.+", "spring-web-6.+"))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), m.getSelect(), m.getArguments().get(0), m.getArguments().get(1));
                }
                return m;
            }
        });
    }
}

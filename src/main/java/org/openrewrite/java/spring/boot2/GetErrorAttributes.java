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
package org.openrewrite.java.spring.boot2;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class GetErrorAttributes extends Recipe {
    private static final MethodMatcher MATCHER = new MethodMatcher("org.springframework.boot.web.servlet.error.ErrorAttributes getErrorAttributes(org.springframework.web.context.request.WebRequest, boolean)");

    @Override
    public String getDisplayName() {
        return "Use `ErrorAttributes#getErrorAttributes(WebRequest, ErrorAttributeOptions)`";
    }

    @Override
    public String getDescription() {
        return "`ErrorAttributes#getErrorAttributes(WebRequest, boolean)` was deprecated in Spring Boot 2.3.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MATCHER), new GetErrorAttributesVisitor());
    }

    private static class GetErrorAttributesVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final String[] parserImports = new String[]{
                "org.springframework.boot.web.error.ErrorAttributeOptions",
                "org.springframework.boot.web.error.ErrorAttributes",
                "org.springframework.web.context.request.WebRequest",
                "org.springframework.web.context.request.RequestAttributes"
        };

        private static boolean isLiteralTrue(@Nullable Expression expression) {
            return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
        }

        private static boolean isLiteralFalse(@Nullable Expression expression) {
            return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (MATCHER.matches(mi)) {
                assert mi.getArguments().size() == 2;
                Expression includeStackTraceArgument = mi.getArguments().get(1);
                if (isLiteralTrue(includeStackTraceArgument)) {
                    String template = "#{any(org.springframework.web.context.request.WebRequest)}, ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.STACK_TRACE)";
                    mi = JavaTemplate.builder(template)
                        .contextSensitive()
                        .imports(parserImports)
                        .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "spring-boot-2", "spring-boot-autoconfigure-2", "spring-web-5"))
                        .build().apply(
                            getCursor(),
                            mi.getCoordinates().replaceArguments(),
                            mi.getArguments().get(0)
                    );
                } else if (isLiteralFalse(includeStackTraceArgument)) {
                    String template = "#{any(org.springframework.web.context.request.WebRequest)}, ErrorAttributeOptions.defaults()";
                    mi = JavaTemplate.builder(template)
                        .contextSensitive()
                        .imports(parserImports)
                        .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "spring-boot-2", "spring-boot-autoconfigure-2", "spring-web-5"))
                        .build()
                        .apply(
                            getCursor(),
                            mi.getCoordinates().replaceArguments(),
                            mi.getArguments().get(0)
                    );
                } else if (!(mi.getArguments().get(1) instanceof J.Ternary)) {
                    String template = "#{any(org.springframework.web.context.request.WebRequest)}, #{any(boolean)} ? ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.STACK_TRACE) : ErrorAttributeOptions.defaults()";
                    mi = JavaTemplate.builder(template)
                        .contextSensitive()
                        .imports(parserImports)
                        .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "spring-boot-2", "spring-boot-autoconfigure-2", "spring-web-5"))
                        .build()
                        .apply(
                            getCursor(),
                            mi.getCoordinates().replaceArguments(),
                            mi.getArguments().toArray()
                    );
                }
                maybeAddImport("org.springframework.boot.web.error.ErrorAttributeOptions");
            }
            return mi;
        }
    }
}

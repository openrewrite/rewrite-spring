/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class GetErrorAttributes extends Recipe {
    private static final MethodMatcher MATCHER = new MethodMatcher("org.springframework.boot.web.servlet.error.ErrorAttributes getErrorAttributes(.., boolean)");

    @Override
    public String getDisplayName() {
        return "Use `ErrorAttributes#getErrorAttributes(WebRequest, ErrorAttributeOptions)`";
    }

    @Override
    public String getDescription() {
        return "`ErrorAttributes#getErrorAttributes(WebRequest, boolean)` was deprecated in Spring Boot 2.3.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(MATCHER);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new GetErrorAttributesVisitor();
    }

    private static class GetErrorAttributesVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String errorAttributeOptionsStub = "" +
                "package org.springframework.boot.web.error;" +
                "import java.io.*;" +
                "public class ErrorAttributeOptions {" +
                "   public boolean isIncluded(ErrorAttributeOptions.Include p0) { return (boolean) (Object) null; }" +
                "   public java.util.Set getIncludes() { return (java.util.Set) (Object) null; }" +
                "   public ErrorAttributeOptions including(ErrorAttributeOptions.Include... p0) { return (ErrorAttributeOptions) (Object) null; }" +
                "   public ErrorAttributeOptions excluding(ErrorAttributeOptions.Include... p0) { return (ErrorAttributeOptions) (Object) null; }" +
                "   public static ErrorAttributeOptions defaults() { return (ErrorAttributeOptions) (Object) null; }" +
                "   public static ErrorAttributeOptions of(ErrorAttributeOptions.Include... p0) { return (ErrorAttributeOptions) (Object) null; }" +
                "   public static ErrorAttributeOptions of(java.util.Collection p0) { return (ErrorAttributeOptions) (Object) null; }" +
                "   public enum Include { EXCEPTION, MESSAGE, STACK_TRACE, BINDING_ERRORS; } " +
                "}" +
                "";

        private static boolean isLiteralTrue(@Nullable Expression expression) {
            return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
        }

        private static boolean isLiteralFalse(@Nullable Expression expression) {
            return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (MATCHER.matches(method)) {
                assert method.getArguments().size() == 2;
                Expression includeStackTraceArgument = method.getArguments().get(1);
                if (isLiteralTrue(includeStackTraceArgument)) {
                    String template = "#{any(org.springframework.web.context.request.WebRequest)}, ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.STACK_TRACE)";
                    method = method.withTemplate(JavaTemplate.builder(this::getCursor, template)
                                    .imports("org.springframework.boot.web.error.ErrorAttributeOptions")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn(errorAttributeOptionsStub)
                                            .build())
                                    .build(),
                            method.getCoordinates().replaceArguments(),
                            method.getArguments().get(0)
                    );
                } else if (isLiteralFalse(includeStackTraceArgument)) {
                    String template = "#{any(org.springframework.web.context.request.WebRequest)}, ErrorAttributeOptions.defaults()";
                    method = method.withTemplate(JavaTemplate.builder(this::getCursor, template)
                                    .imports("org.springframework.boot.web.error.ErrorAttributeOptions")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn(errorAttributeOptionsStub)
                                            .build())
                                    .build(),
                            method.getCoordinates().replaceArguments(),
                            method.getArguments().get(0)
                    );
                } else {
                    String template = "#{any(org.springframework.web.context.request.WebRequest)}, #{any(boolean)} ? ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.STACK_TRACE) : ErrorAttributeOptions.defaults()";
                    method = method.withTemplate(JavaTemplate.builder(this::getCursor, template)
                                    .imports("org.springframework.boot.web.error.ErrorAttributeOptions")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn(errorAttributeOptionsStub)
                                            .build())
                                    .build(),
                            method.getCoordinates().replaceArguments(),
                            method.getArguments().toArray()
                    );
                }
                maybeAddImport("org.springframework.boot.web.error.ErrorAttributeOptions");
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}


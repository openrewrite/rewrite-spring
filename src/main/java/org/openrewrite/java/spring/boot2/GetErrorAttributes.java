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
    private static final MethodMatcher MATCHER = new MethodMatcher("org.springframework.boot.web.servlet.error.ErrorAttributes getErrorAttributes(org.springframework.web.context.request.WebRequest, boolean)");

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
        private final String[] webRequestErrorAttrShims = new String[]{
                "package org.springframework.boot.web.error;" +
                "public class ErrorAttributeOptions {" +
                "   public boolean isIncluded(ErrorAttributeOptions.Include p0) { return (boolean) (Object) null; }" +
                "   public java.util.Set getIncludes() { return (java.util.Set) (Object) null; }" +
                "   public ErrorAttributeOptions including(ErrorAttributeOptions.Include... p0) { return (ErrorAttributeOptions) (Object) null; }" +
                "   public ErrorAttributeOptions excluding(ErrorAttributeOptions.Include... p0) { return (ErrorAttributeOptions) (Object) null; }" +
                "   public static ErrorAttributeOptions defaults() { return (ErrorAttributeOptions) (Object) null; }" +
                "   public static ErrorAttributeOptions of(ErrorAttributeOptions.Include... p0) { return (ErrorAttributeOptions) (Object) null; }" +
                "   public static ErrorAttributeOptions of(java.util.Collection p0) { return (ErrorAttributeOptions) (Object) null; }" +
                "   public enum Include { EXCEPTION, MESSAGE, STACK_TRACE, BINDING_ERRORS; } " +
                "}",
                "package org.springframework.boot.web.servlet.error;" +
                "import org.springframework.web.context.request.WebRequest;" +
                "import org.springframework.boot.web.error.ErrorAttributeOptions;" +
                "import java.util.Map;" +
                "public interface ErrorAttributes {" +
                "   Map<String, Object> getErrorAttributes(WebRequest webRequest, boolean includeStackTrace);" +
                "   Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options);" +
                "   Throwable getError(WebRequest webRequest);" +
                "}",
                "package org.springframework.web.context.request;" +
                "public interface RequestAttributes {" +
                "    int SCOPE_REQUEST = 0;" +
                "    int SCOPE_SESSION = 1;" +
                "    int SCOPE_GLOBAL_SESSION = 2;" +
                "    String REFERENCE_REQUEST = \"request\";" +
                "    String REFERENCE_SESSION = \"session\";" +
                "    Object getAttribute(String name, int scope);" +
                "    void setAttribute(String name, Object value, int scope);" +
                "    void removeAttribute(String name, int scope);" +
                "    String[] getAttributeNames(int scope);" +
                "    void registerDestructionCallback(String name, Runnable callback, int scope);" +
                "    Object resolveReference(String key);" +
                "    String getSessionId();" +
                "    Object getSessionMutex();" +
                "}",
                "package org.springframework.web.context.request;" +
                "import java.util.Iterator;" +
                "import java.util.Locale;" +
                "import java.util.Map;" +
                "public interface WebRequest extends RequestAttributes {" +
                "   String getHeader(String headerName);" +
                "   String[] getHeaderValues(String headerName);" +
                "   Iterator<String> getHeaderNames();" +
                "   String getParameter(String paramName);" +
                "   String[] getParameterValues(String paramName);" +
                "   Iterator<String> getParameterNames();" +
                "   Map<String, String[]> getParameterMap();" +
                "   Locale getLocale();" +
                "   String getContextPath();" +
                "   String getRemoteUser();" +
                "   boolean isUserInRole(String role);" +
                "   boolean isSecure();" +
                "   boolean checkNotModified(long lastModifiedTimestamp);" +
                "   boolean checkNotModified(String etag);" +
                "   boolean checkNotModified(String etag, long lastModifiedTimestamp);" +
                "   String getDescription(boolean includeClientInfo);" +
                "}"};
        private final String[] parserImports = new String[]{"org.springframework.boot.web.error.ErrorAttributeOptions","org.springframework.boot.web.error.ErrorAttributes", "org.springframework.web.context.request.WebRequest", "org.springframework.web.context.request.RequestAttributes"};

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
                    mi = mi.withTemplate(JavaTemplate.builder(this::getCursor, template)
                                    .imports(parserImports)
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn(webRequestErrorAttrShims)
                                            .build())
                                    .build(),
                            mi.getCoordinates().replaceArguments(),
                            mi.getArguments().get(0)
                    );
                } else if (isLiteralFalse(includeStackTraceArgument)) {
                    String template = "#{any(org.springframework.web.context.request.WebRequest)}, ErrorAttributeOptions.defaults()";
                    mi = mi.withTemplate(JavaTemplate.builder(this::getCursor, template)
                                    .imports(parserImports)
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn(webRequestErrorAttrShims)
                                            .build())
                                    .build(),
                            mi.getCoordinates().replaceArguments(),
                            mi.getArguments().get(0)
                    );
                } else if (!(mi.getArguments().get(1) instanceof J.Ternary)){
                    String template = "#{any(org.springframework.web.context.request.WebRequest)}, #{any(boolean)} ? ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.STACK_TRACE) : ErrorAttributeOptions.defaults()";
                    mi = mi.withTemplate(JavaTemplate.builder(this::getCursor, template)
                                    .imports(parserImports)
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn(webRequestErrorAttrShims)
                                            .build())
                                    .build(),
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


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
import org.openrewrite.java.tree.J;

public class MigrateUriComponentsBuilderMethods extends Recipe {

    private static final String TARGET_CLASS = "org.springframework.web.util.UriComponentsBuilder";

    private static final MethodMatcher FROM_HTTP_REQUEST = new MethodMatcher(TARGET_CLASS + " fromHttpRequest(org.springframework.http.HttpRequest)");

    private static final MethodMatcher PARSE_FORWARDED_FOR = new MethodMatcher(TARGET_CLASS + " parseForwardedFor(org.springframework.http.HttpRequest, java.net.InetSocketAddress)");

    @Override
    public String getDisplayName() {
        return "Migrate `UriComponentsBuilder.fromHttpRequest` and `parseForwardedFor`";
    }

    @Override
    public String getDescription() {
        return "The `fromHttpRequest` and `parseForwardedFor` methods in `org.springframework.web.util.UriComponentsBuilder` were deprecated, in favor of `org.springframework.web.util.ForwardedHeaderUtils`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(TARGET_CLASS + " *(..)", false), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (FROM_HTTP_REQUEST.matches(mi)) {
                    maybeAddImport("org.springframework.web.util.ForwardedHeaderUtils");
                    maybeRemoveImport("org.springframework.web.util.UriComponentsBuilder");
                    return JavaTemplate.builder("ForwardedHeaderUtils.adaptFromForwardedHeaders(#{any()}.getURI(), #{any()}.getHeaders())")
                        .imports("org.springframework.web.util.ForwardedHeaderUtils")
                        .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-web-6.1.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), mi.getArguments().get(0), mi.getArguments().get(0));
                }
                if (PARSE_FORWARDED_FOR.matches(mi)) {
                    maybeAddImport("org.springframework.web.util.ForwardedHeaderUtils");
                    maybeRemoveImport("org.springframework.web.util.UriComponentsBuilder");
                    return JavaTemplate.builder("ForwardedHeaderUtils.parseForwardedFor(#{any()}.getURI(), #{any()}.getHeaders(), #{any()})")
                        .imports("org.springframework.web.util.ForwardedHeaderUtils")
                        .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-web-6.1.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), mi.getArguments().get(0), mi.getArguments().get(0), mi.getArguments().get(1));
                }
                return mi;
            }
        });
    }
}

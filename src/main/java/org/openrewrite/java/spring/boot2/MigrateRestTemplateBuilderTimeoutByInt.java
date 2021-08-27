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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class MigrateRestTemplateBuilderTimeoutByInt extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `RestTemplateBuilder#setConnectTimeout(Duration)` and `RestTemplateBuilder#setReadTimeout(Duration)`";
    }

    @Override
    public String getDescription() {
        return "`RestTemplateBuilder#setConnectTimeout(int)` and `RestTemplateBuilder#setReadTimeout(int)` were deprecated in Spring Boot 2.1.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.boot.web.client.RestTemplateBuilder");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final MethodMatcher connectionTimeout = new MethodMatcher("org.springframework.boot.web.client.RestTemplateBuilder setConnectTimeout(int)");
            final MethodMatcher readTimeout = new MethodMatcher("org.springframework.boot.web.client.RestTemplateBuilder setReadTimeout(int)");

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
                J.MethodInvocation m = super.visitMethodInvocation(method, context);
                if (connectionTimeout.matches(method) || readTimeout.matches(method)) {
                    m = m.withTemplate(
                            JavaTemplate
                                    .builder(this::getCursor,"Duration.ofMillis(#{any(int)})")
                                    .imports("java.time.Duration")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn("package org.springframework.boot.web.client;" +
                                                    "import java.time.Duration;" +
                                                    "public class RestTemplateBuilder {" +
                                                    "public RestTemplateBuilder setConnectTimeout(java.time.Duration) { return null; }" +
                                                    "public RestTemplateBuilder setReadTimeout(java.time.Duration) { return null; }" +
                                                    "}")
                                            .build())
                                    .build(),
                            m.getCoordinates().replaceArguments(),
                            m.getArguments().get(0));
                    maybeAddImport("java.time.Duration");
                }
                return m;
            }
        };
    }
}

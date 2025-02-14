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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.boot.web.client.RestTemplateBuilder", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    final MethodMatcher connectionTimeout = new MethodMatcher("org.springframework.boot.web.client.RestTemplateBuilder setConnectTimeout(int)");
                    final MethodMatcher readTimeout = new MethodMatcher("org.springframework.boot.web.client.RestTemplateBuilder setReadTimeout(int)");

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        updateCursor(m);
                        if (connectionTimeout.matches(m) || readTimeout.matches(m)) {
                            m = JavaTemplate
                                    .builder("Duration.ofMillis(#{any(int)})")
                                    .imports("java.time.Duration")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "spring-boot-2.*"))
                                    .build()
                                    .apply(
                                            getCursor(),
                                            m.getCoordinates().replaceArguments(),
                                            m.getArguments().get(0));
                            maybeAddImport("java.time.Duration");
                        }
                        return m;
                    }
                });
    }
}

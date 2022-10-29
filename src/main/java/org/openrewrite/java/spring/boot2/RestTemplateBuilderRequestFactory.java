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
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * <a href="https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.0-Migration-Guide#resttemplatebuilder">...</a>
 */
public class RestTemplateBuilderRequestFactory extends Recipe {
    private static final MethodMatcher REQUEST_FACTORY = new MethodMatcher(
            "org.springframework.boot.web.client.RestTemplateBuilder requestFactory(org.springframework.http.client.ClientHttpRequestFactory)");

    @Override
    public String getDisplayName() {
        return "Migrate `RestTemplateBuilder`";
    }

    @Override
    public String getDescription() {
        return "Migrate `RestTemplateBuilder#requestFactory` calls to use a `Supplier`.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.boot.web.client.RestTemplateBuilder");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RestTemplateBuilderRequestFactoryVisitor();
    }

    private static class RestTemplateBuilderRequestFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

            // TODO JavaTemplate doesn't replace method type attribution when replacing arguments.
            boolean isArgumentClientHttpRequestFactory = method.getArguments().size() == 1 &&
                    TypeUtils.isAssignableTo(JavaType.ShallowClass.build("org.springframework.http.client.ClientHttpRequestFactory"),
                            method.getArguments().get(0).getType());

            if (REQUEST_FACTORY.matches(method) && isArgumentClientHttpRequestFactory) {
                JavaTemplate.Builder t = JavaTemplate.builder(this::getCursor, "() -> #{any(org.springframework.http.client.ClientHttpRequestFactory)}")
                        .javaParser(() -> JavaParser.fromJavaVersion()
                                .dependsOn(Parser.Input.fromResource("/RestTemplateBuilder.java", "---"))
                                .build());
                m = m.withTemplate(t.build(), m.getCoordinates().replaceArguments(), m.getArguments().get(0));
            }
            return m;
        }
    }
}

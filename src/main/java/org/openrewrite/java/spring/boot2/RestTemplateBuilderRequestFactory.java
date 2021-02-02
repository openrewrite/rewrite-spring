/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

/**
 * https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.0-Migration-Guide#resttemplatebuilder
 */
public class RestTemplateBuilderRequestFactory extends Recipe {
    private static final MethodMatcher requestFactory = new MethodMatcher(
            "org.springframework.boot.web.client.RestTemplateBuilder requestFactory(org.springframework.http.client.ClientHttpRequestFactory)");

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RestTemplateBuilderRequestFactoryVisitor();
    }

    private static class RestTemplateBuilderRequestFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {
        public RestTemplateBuilderRequestFactoryVisitor() {
            setCursoringOn();
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            if (requestFactory.matches(method)) {
                m = maybeAutoFormat(m,
                        m.withArgs(JavaTemplate.builder("() -> #{}").build().generate(getCursor(),m.getArgs().get(0))), ctx);
            }
            return m;
        }
    }
}

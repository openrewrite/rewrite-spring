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

import lombok.Getter;
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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Collections.singletonList;

public class RestTemplateBuilderRequestFactory extends Recipe {
    private static final MethodMatcher REQUEST_FACTORY = new MethodMatcher(
            "org.springframework.boot.web.client.RestTemplateBuilder requestFactory(org.springframework.http.client.ClientHttpRequestFactory)");

    @Getter
    final String displayName = "Migrate `RestTemplateBuilder`";

    @Getter
    final String description = "Migrate `RestTemplateBuilder#requestFactory` calls to use a `Supplier`. " +
            "See the [migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.0-Migration-Guide#resttemplatebuilder) for more.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(REQUEST_FACTORY),
                new RestTemplateBuilderRequestFactoryVisitor());
    }

    private static class RestTemplateBuilderRequestFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

            boolean isArgumentClientHttpRequestFactory = method.getArguments().size() == 1 &&
                    TypeUtils.isAssignableTo(JavaType.ShallowClass.build("org.springframework.http.client.ClientHttpRequestFactory"),
                            method.getArguments().get(0).getType());

            if (REQUEST_FACTORY.matches(method) && isArgumentClientHttpRequestFactory) {
                JavaTemplate.Builder t = JavaTemplate.builder("() -> #{any(org.springframework.http.client.ClientHttpRequestFactory)}")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-boot-2.*"));
                m = t.build().apply(getCursor(), m.getCoordinates().replaceArguments(), m.getArguments().get(0));
                // JavaTemplate doesn't replace the method type when replacing arguments, so point it at `requestFactory(Supplier)`
                JavaType.Method oldType = method.getMethodType();
                if (oldType != null) {
                    JavaType.Parameterized supplier = new JavaType.Parameterized(null,
                            JavaType.ShallowClass.build("java.util.function.Supplier"),
                            singletonList(oldType.getParameterTypes().get(0)));
                    m = m.withMethodType(oldType.withParameterTypes(singletonList(supplier)));
                }
            }
            return m;
        }
    }
}

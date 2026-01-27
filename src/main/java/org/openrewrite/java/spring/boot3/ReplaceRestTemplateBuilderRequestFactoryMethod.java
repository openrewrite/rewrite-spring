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
package org.openrewrite.java.spring.boot3;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class ReplaceRestTemplateBuilderRequestFactoryMethod extends Recipe {

    private static final MethodMatcher REQUEST_FACTORY_MATCHER = new MethodMatcher("org.springframework.boot.web.client.RestTemplateBuilder requestFactory(java.util.function.Function)");

    @Getter
    final String displayName = "Replace `RestTemplateBuilder.requestFactory(Function)` with `requestFactoryBuilder`";

    @Getter
    final String description = "`RestTemplateBuilder.requestFactory(java.util.function.Function)` was deprecated since Spring Boot 3.4, in favor of `requestFactoryBuilder(ClientHttpRequestFactoryBuilder)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(REQUEST_FACTORY_MATCHER), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (REQUEST_FACTORY_MATCHER.matches(mi) && mi.getSelect() != null) {
                    J replacement = JavaTemplate.builder("#{any()}.requestFactoryBuilder(settings -> #{any()}.apply(ClientHttpRequestFactorySettings.of(settings)))")
                            .imports("org.springframework.boot.web.client.ClientHttpRequestFactorySettings")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-web-6.2", "spring-boot-3.+"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect(), mi.getArguments().get(0));
                    doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(replacement));
                    return replacement;
                }
                return mi;
            }
        });
    }
}

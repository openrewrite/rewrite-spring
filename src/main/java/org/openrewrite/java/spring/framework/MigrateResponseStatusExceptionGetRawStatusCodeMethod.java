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
package org.openrewrite.java.spring.framework;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class MigrateResponseStatusExceptionGetRawStatusCodeMethod extends Recipe {
    private static final MethodMatcher RESPONSE_STATUS_EXCEPTION_MATCHER =
            new MethodMatcher("org.springframework.web.server.ResponseStatusException getRawStatusCode()");
    private static final MethodMatcher REST_CLIENT_RESPONSE_EXCEPTION_MATCHER =
            new MethodMatcher("org.springframework.web.client.RestClientResponseException getRawStatusCode()");

    @Getter
    final String displayName = "Migrate `ResponseStatusException#getRawStatusCode()` to `getStatusCode().value()`";

    @Getter
    final String description = "Migrate Spring Framework 5.3's `ResponseStatusException` method `getRawStatusCode()` to Spring Framework 6's " +
            "`getStatusCode().value()`. Also handles `RestClientResponseException` and its subclasses such as `HttpServerErrorException`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (RESPONSE_STATUS_EXCEPTION_MATCHER.matches(m) || REST_CLIENT_RESPONSE_EXCEPTION_MATCHER.matches(m)) {
                    return JavaTemplate.builder("#{any()}.getStatusCode().value()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-core-6", "spring-beans-6", "spring-web-6"))
                            .build()
                            .apply(updateCursor(m), m.getCoordinates().replace(), m.getSelect());
                }
                return m;
            }
        };
    }
}

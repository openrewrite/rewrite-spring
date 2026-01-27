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

import lombok.Getter;
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

public class MigrateClientHttpResponseGetRawStatusCodeMethod extends Recipe {
    private static final MethodMatcher MATCHER = new MethodMatcher("org.springframework.http.client.ClientHttpResponse getRawStatusCode()");

    @Getter
    final String displayName = "Replaces deprecated `ClientHttpResponse#getRawStatusCode()`";

    @Getter
    final String description = "`ClientHttpResponse#getRawStatusCode()` was deprecated, so we replace it with `getStatusCode()`, " +
            "though the return type has changed from `int` to `HttpStatusCode`, so we must account for that as well.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MATCHER), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (MATCHER.matches(m)) {
                    return JavaTemplate.builder("#{any(org.springframework.http.client.ClientHttpResponse)}.getStatusCode().value()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-web-6"))
                            .build()
                            .apply(updateCursor(m), m.getCoordinates().replace(), m.getSelect());
                }
                return m;
            }
        });
    }
}

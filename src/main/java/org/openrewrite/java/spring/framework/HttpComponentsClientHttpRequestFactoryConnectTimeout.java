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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;

public class HttpComponentsClientHttpRequestFactoryConnectTimeout extends Recipe {
    private static final MethodMatcher SET_CONNECT_TIMEOUT_METHOD_MATCHER =
            new MethodMatcher("org.springframework.http.client.HttpComponentsClientHttpRequestFactory setConnectTimeout(..)");

    @Getter
    final String displayName = "Migrate `setConnectTimeout(..)` to ConnectionConfig `setConnectTimeout(..)`";

    @Getter
    final String description = "`setConnectTimeout(..)` was deprecated in Spring Framework 6.2 and removed in 7.0. " +
                               "This recipe adds a comment directing users to migrate to `ConnectionConfig.setConnectTimeout()` " +
                               "on the `PoolingHttpClientConnectionManager`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SET_CONNECT_TIMEOUT_METHOD_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (SET_CONNECT_TIMEOUT_METHOD_MATCHER.matches(method)) {
                    String message = " Manual migration to `ConnectionConfig.Builder.setConnectTimeout(Timeout)` necessary; see: " +
                                     "https://github.com/spring-projects/spring-framework/issues/35748";
                    if (method.getComments().stream().noneMatch(c -> c.printComment(getCursor()).contains(message))) {
                        return method.withPrefix(method.getPrefix().withComments(ListUtils.concat(method.getComments(),
                                new TextComment(false, message, "\n" + method.getPrefix().getIndent(), Markers.EMPTY)
                        )));
                    }
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }
}

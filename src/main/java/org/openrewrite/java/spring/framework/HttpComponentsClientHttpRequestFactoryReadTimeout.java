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
package org.openrewrite.java.spring.framework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class HttpComponentsClientHttpRequestFactoryReadTimeout extends Recipe {
    private static final MethodMatcher MATCHER = new MethodMatcher("org.springframework.http.client.HttpComponentsClientHttpRequestFactory setReadTimeout(..)");

    @Override
    public String getDisplayName() {
        return "Migrate `setReadTimeout(java.lang.int)` to SocketConfig `setSoTimeout(..)`";
    }

    @Override
    public String getDescription() {
        return "`setReadTimeout(..)` was removed in Spring Framework 6.1.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MATCHER), new HttpComponentsClientHttpRequestFactoryReadTimeoutVisitor());
    }

    private static class HttpComponentsClientHttpRequestFactoryReadTimeoutVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (MATCHER.matches(method)) {
                Expression expression = method.getArguments().get(0);
                doAfterVisit(new JavaIsoVisitor<ExecutionContext>(){

                });
                return null;
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}

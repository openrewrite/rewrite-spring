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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class MigrateHandlerResultHasExceptionHandlerMethod extends Recipe {

    private static final String HandlerResult = "org.springframework.web.reactive.HandlerResult";

    private static final MethodMatcher METHOD_MATCHER = new MethodMatcher(HandlerResult + " hasExceptionHandler()");

    private static final JavaTemplate replacementTemplate = JavaTemplate
        .builder("#{any(org.springframework.web.reactive.HandlerResult)}.getExceptionHandler() != null")
        .build();

    @Override
    public String getDisplayName() {
        return "Migrate `org.springframework.web.reactive.HandlerResult.hasExceptionHandler` method";
    }

    @Override
    public String getDescription() {
        return "`org.springframework.web.reactive.HandlerResult.hasExceptionHandler()` was deprecated, in favor of `getExceptionHandler()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(HandlerResult, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (METHOD_MATCHER.matches(mi)) {
                    return replacementTemplate.apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect());
                }
                return mi;
            }
        });
    }
}

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
package org.openrewrite.java.spring.swagger;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class RemoveBuild extends Recipe {
    private static final MethodMatcher BUILD_MATCHER = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder build()");

    @Override
    public String getDisplayName() {
        return "Remove `ApiInfoBuilder.build()`";
    }

    @Override
    public String getDescription() {
        return "Remove SpringFox's `ApiInfoBuilder.build()` ahead of migration to Swagger's `Info`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(BUILD_MATCHER), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (BUILD_MATCHER.matches(method)) {
                    return maybeAutoFormat(method, method.getSelect().withPrefix(method.getPrefix()), ctx);
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }
}

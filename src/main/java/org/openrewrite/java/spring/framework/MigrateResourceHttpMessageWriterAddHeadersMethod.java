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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class MigrateResourceHttpMessageWriterAddHeadersMethod extends Recipe {

    private static final String TARGET_CLASS = "org.springframework.http.codec.ResourceHttpMessageWriter";

    private static final MethodMatcher METHOD_MATCHER = new MethodMatcher(TARGET_CLASS +
        " addHeaders(org.springframework.http.ReactiveHttpOutputMessage, org.springframework.core.io.Resource, org.springframework.http.MediaType, java.util.Map)");

    @Override
    public String getDisplayName() {
        return "Migrate `org.springframework.http.codec.ResourceHttpMessageWriter.addHeaders` method";
    }

    @Override
    public String getDescription() {
        return "`Migrate `org.springframework.http.codec.ResourceHttpMessageWriter.addHeaders` was deprecated, in favor of `addDefaultHeaders` method.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(TARGET_CLASS, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (METHOD_MATCHER.matches(m)) {
                    return JavaTemplate.builder("#{any()}.addDefaultHeaders(#{any()}, #{any()}, #{any()}, #{any()}).block()")
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), m.getSelect(), m.getArguments().get(0), m.getArguments().get(1), m.getArguments().get(2), m.getArguments().get(3));
                }
                return m;
            }
        });
    }

}

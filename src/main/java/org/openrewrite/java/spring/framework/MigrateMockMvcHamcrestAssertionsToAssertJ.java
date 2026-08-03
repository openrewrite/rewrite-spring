/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

public class MigrateMockMvcHamcrestAssertionsToAssertJ extends Recipe {
    private static final String MOCK_MVC_TESTER = "org.springframework.test.web.servlet.assertj.MockMvcTester";
    private static final String ASSERT_THAT = "org.assertj.core.api.Assertions.assertThat";

    private static final MethodMatcher AND_EXPECT = new MethodMatcher(
            "org.springframework.test.web.servlet.ResultActions andExpect(org.springframework.test.web.servlet.ResultMatcher)"
    );
    private static final MethodMatcher PERFORM = new MethodMatcher(
            "org.springframework.test.web.servlet.MockMvc perform(org.springframework.test.web.servlet.RequestBuilder)"
    );

    @Getter
    final String displayName = "Migrate MockMvc Hamcrest assertions to AssertJ";

    @Getter
    final String description = "Wraps chained MockMvc `andExpect(..)` assertions in Spring Framework 6.2's AssertJ support while preserving the existing request builders and result matchers.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(AND_EXPECT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!AND_EXPECT.matches(mi) || isChainedAndExpect()) {
                    return mi;
                }

                List<Expression> matchers = new ArrayList<>();
                J.MethodInvocation current = mi;
                while (AND_EXPECT.matches(current)) {
                    matchers.add(0, current.getArguments().get(0));
                    if (!(current.getSelect() instanceof J.MethodInvocation)) {
                        return mi;
                    }
                    current = (J.MethodInvocation) current.getSelect();
                }

                if (!PERFORM.matches(current) || current.getSelect() == null || current.getArguments().size() != 1) {
                    return mi;
                }

                StringBuilder template = new StringBuilder("assertThat(MockMvcTester.create(#{any()}).perform(#{any()}))");
                for (int i = 0; i < matchers.size(); i++) {
                    template.append(".matches(#{any()})");
                }

                List<Object> parameters = new ArrayList<>();
                parameters.add(current.getSelect());
                parameters.add(current.getArguments().get(0));
                parameters.addAll(matchers);

                return JavaTemplate.builder(template.toString())
                        .imports(MOCK_MVC_TESTER)
                        .staticImports(ASSERT_THAT)
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), parameters.toArray());
            }

            private boolean isChainedAndExpect() {
                Object parent = getCursor().getParentOrThrow().getValue();
                if (!(parent instanceof J.MethodInvocation)) {
                    return false;
                }
                J.MethodInvocation parentInvocation = (J.MethodInvocation) parent;
                return AND_EXPECT.matches(parentInvocation) && parentInvocation.getSelect() instanceof J.MethodInvocation;
            }
        });
    }
}

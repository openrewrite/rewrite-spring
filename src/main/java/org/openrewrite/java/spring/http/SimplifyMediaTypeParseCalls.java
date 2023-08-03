/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.http;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class SimplifyMediaTypeParseCalls extends Recipe {
    @Override
    public String getDisplayName() {
        return "Simplify Unnecessary `MediaType.parse` calls";
    }

    @Override
    public String getDescription() {
        return "Replaces `MediaType.parse('application/json')` with `MediaType.APPLICATION_JSON`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyParseCallsVisitor();
    }

    private static final class SimplifyParseCallsVisitor extends JavaVisitor<ExecutionContext> {
        private final MethodMatcher MEDIATYPE_PARSE_MATCHER = new MethodMatcher("org.springframework.http.MediaType parse(..)");

        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            if (!MEDIATYPE_PARSE_MATCHER.matches(mi) && !mi.getArguments().isEmpty()) {
                return mi;
            }

            J.Literal test = (J.Literal) mi.getArguments().get(0);
            if (!test.getValue().equals("application/json")) {
                return mi;
            }

            return JavaTemplate.builder("MediaType.APPLICATION_JSON")
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace());
        }
    }
}

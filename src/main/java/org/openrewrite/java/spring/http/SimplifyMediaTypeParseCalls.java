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
        return "Simplify Unnecessary `MediaType.parseMediaType` and `MediaType.valueOf` calls";
    }

    @Override
    public String getDescription() {
        return "Replaces `MediaType.parseMediaType('application/json')` and `MediaType.valueOf('application/json') with `MediaType.APPLICATION_JSON`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyParseCallsVisitor();
    }

    private static final class SimplifyParseCallsVisitor extends JavaVisitor<ExecutionContext> {
        private final MethodMatcher MEDIATYPE_PARSE_MEDIA_TYPE_MATCHER = new MethodMatcher("org.springframework.http.MediaType parseMediaType(String)");
        private final MethodMatcher MEDIATYPE_VALUE_OF_MATCHER = new MethodMatcher("org.springframework.http.MediaType valueOf(String)");

        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            if (MEDIATYPE_PARSE_MEDIA_TYPE_MATCHER.matches(mi) || MEDIATYPE_VALUE_OF_MATCHER.matches(mi)) {
                J.Literal test = (J.Literal) mi.getArguments().get(0);
                if ("application/json".equals(test.getValue())) {
                    return JavaTemplate.builder("MediaType.APPLICATION_JSON")
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace());
                }
            }

            return super.visitMethodInvocation(mi, ctx);
        }
    }
}

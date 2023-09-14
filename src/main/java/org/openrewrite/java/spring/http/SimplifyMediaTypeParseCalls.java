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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class SimplifyMediaTypeParseCalls extends Recipe {

    @Override
    public String getDisplayName() {
        return "Simplify Unnecessary `MediaType.parseMediaType` and `MediaType.valueOf` calls";
    }

    @Override
    public String getDescription() {
        return "Replaces `MediaType.parseMediaType('application/json')` and `MediaType.valueOf('application/json')` with `MediaType.APPLICATION_JSON`.";
    }

    static final String MEDIA_TYPE = "org.springframework.http.MediaType";
    static final String PARSE_MEDIA_TYPE = MEDIA_TYPE + " parseMediaType(String)";
    static final String VALUE_OF = MEDIA_TYPE + " valueOf(String)";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(PARSE_MEDIA_TYPE), new UsesMethod<>(VALUE_OF)),
                new SimplifyParseCallsVisitor());
    }

    private static final class SimplifyParseCallsVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
            J j = super.visitMethodInvocation(methodInvocation, ctx);
            J.MethodInvocation mi = (J.MethodInvocation) j;
            if (new MethodMatcher(PARSE_MEDIA_TYPE).matches(mi) || new MethodMatcher(VALUE_OF).matches(mi)) {
                Expression methodArg = mi.getArguments().get(0);
                if (methodArg instanceof J.FieldAccess
                        && TypeUtils.isOfClassType(((J.FieldAccess) methodArg).getTarget().getType(), MEDIA_TYPE)) {
                    maybeRemoveImport(MEDIA_TYPE + ".parseMediaType");
                    maybeRemoveImport(MEDIA_TYPE + ".valueOf");
                    J.FieldAccess fieldAccess = (J.FieldAccess) methodArg;
                    String replacementConstant = fieldAccess.getSimpleName().replace("_VALUE", "");
                    return fieldAccess
                            .withType(JavaType.Primitive.String)
                            .withName(fieldAccess.getName().withSimpleName(replacementConstant))
                            .withPrefix(mi.getPrefix())
                            .withMarkers(mi.getMarkers())
                            .withComments(mi.getComments());
                }
            }
            return j;
        }
    }
}

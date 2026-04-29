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
import org.jspecify.annotations.Nullable;
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

public class UseObjectUtilsIsEmpty extends Recipe {

    private static final MethodMatcher STRING_UTILS_IS_EMPTY =
            new MethodMatcher("org.springframework.util.StringUtils isEmpty(Object)");

    @Getter
    final String displayName = "Use `ObjectUtils#isEmpty(Object)`";

    @Getter
    final String description = "`StringUtils#isEmpty(Object)` was deprecated in 5.3.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(STRING_UTILS_IS_EMPTY), new JavaVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!STRING_UTILS_IS_EMPTY.matches(mi)) {
                    return mi;
                }
                if (hasCollectionLikeArgument(mi)) {
                    return mi;
                }

                maybeRemoveImport("org.springframework.util.StringUtils");
                maybeAddImport("org.springframework.util.ObjectUtils");

                JavaType.Method methodType = mi.getMethodType();
                if (methodType != null) {
                    mi = mi.withMethodType(methodType.withDeclaringType(
                            JavaType.ShallowClass.build("org.springframework.util.ObjectUtils")));
                }
                if (mi.getSelect() instanceof J.Identifier) {
                    mi = mi.withSelect(((J.Identifier) mi.getSelect())
                            .withSimpleName("ObjectUtils")
                            .withType(JavaType.buildType("org.springframework.util.ObjectUtils")));
                }
                return mi;
            }

            private boolean hasCollectionLikeArgument(J.MethodInvocation mi) {
                if (mi.getArguments().isEmpty()) {
                    return false;
                }
                Expression arg = mi.getArguments().get(0);
                @Nullable JavaType argType = arg.getType();
                if (argType == null) {
                    return false;
                }
                if (argType instanceof JavaType.Array) {
                    return true;
                }
                return TypeUtils.isAssignableTo("java.util.Collection", argType) ||
                        TypeUtils.isAssignableTo("java.util.Map", argType) ||
                        TypeUtils.isAssignableTo("java.util.Optional", argType);
            }
        });
    }
}

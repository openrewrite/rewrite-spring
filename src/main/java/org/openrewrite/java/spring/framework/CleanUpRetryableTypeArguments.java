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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;

public class CleanUpRetryableTypeArguments extends Recipe {

    @Getter
    final String displayName = "Clean up type arguments for `Retryable`";

    @Getter
    final String description = "Cleans up type arguments of `Retryable` to keep only the first generic parameter after migrating from `RetryCallback`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.core.retry.Retryable", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ParameterizedType visitParameterizedType(J.ParameterizedType parameterizedType, ExecutionContext ctx) {
                J.ParameterizedType p = super.visitParameterizedType(parameterizedType, ctx);
                if (TypeUtils.isOfClassType(p.getType(), "org.springframework.core.retry.Retryable")) {
                    if (p.getTypeParameters() != null && p.getTypeParameters().size() > 1) {
                        p = p.withTypeParameters(Collections.singletonList(p.getTypeParameters().get(0)));
                    }
                }
                return p;
            }
        });
    }
}

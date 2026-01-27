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
package org.openrewrite.java.spring.security6;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveMethodInvocationsVisitor;
import org.openrewrite.java.tree.Expression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class RemoveFilterSecurityInterceptorOncePerRequest extends Recipe {
    @Getter
    final String displayName = "Remove unnecessary `filterSecurityInterceptorOncePerRequest(false)` when upgrading to Spring Security 6";

    @Getter
    final String description = "In Spring Security 6.0, `<http>` defaults `authorizeRequests#filterSecurityInterceptorOncePerRequest` to false. " +
            "So, to complete migration, any defaults values can be removed.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        Map<MethodMatcher, Predicate<List<Expression>>> matchers = new HashMap<>();
        matchers.put(new MethodMatcher("org.springframework.security.config.annotation.web.configurers.AbstractInterceptUrlConfigurer.AbstractInterceptUrlRegistry filterSecurityInterceptorOncePerRequest(boolean)"
            ), RemoveMethodInvocationsVisitor.isFalseArgument());
        return new RemoveMethodInvocationsVisitor(matchers);
    }
}

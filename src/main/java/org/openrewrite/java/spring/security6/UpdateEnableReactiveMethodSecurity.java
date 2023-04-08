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
package org.openrewrite.java.spring.security6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.List;

public class UpdateEnableReactiveMethodSecurity extends Recipe {

    private static final AnnotationMatcher ENABLE_REACTIVE_METHOD_SECURITY_MATCHER =
        new AnnotationMatcher("@org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity");

    @Override
    public String getDisplayName() {
        return "Remove the `useAuthorizationManager=true` attribute from `@EnableReactiveMethodSecurity`";
    }

    @Override
    public String getDescription() {
        return "In Spring security 6.0, `@EnableReactiveMethodSecurity` defaults `useAuthorizationManager` to true. " +
               "So, to complete migration, `@EnableReactiveMethodSecurity` remove the `useAuthorizationManager` attribute.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>(
            "org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity", false);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                annotation = super.visitAnnotation(annotation, ctx);
                if (ENABLE_REACTIVE_METHOD_SECURITY_MATCHER.matches(annotation) &&
                    annotation.getArguments() != null &&
                    !annotation.getArguments().isEmpty()) {
                    List<Expression> args = annotation.getArguments();
                    args = ListUtils.map(args, arg -> isUseAuthorizationManagerArgSetToTrue(arg) ? null : arg);
                    return autoFormat(annotation.withArguments(args), ctx);
                }
                return annotation;
            }
        };
    }

    private static boolean isUseAuthorizationManagerArgSetToTrue(Expression arg) {
        if (arg instanceof J.Assignment) {
            J.Assignment assignment = (J.Assignment) arg;
            return assignment.getVariable().toString().equals("useAuthorizationManager") &&
                   RequireExplicitSavingOfSecurityContextRepository.isTrue(assignment.getAssignment());
        }
        return false;
    }
}

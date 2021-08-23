/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class BeanMethodsNotPublic extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove `public` from `@Bean` methods";
    }

    @Override
    public String getDescription() {
        return "Remove public modifier from `@Bean` methods. They no longer have to be public visibility to be usable by Spring.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.context.annotation.Bean");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new BeanMethodsNotPublicVisitor();
    }

    private static class BeanMethodsNotPublicVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher BEAN_ANNOTATION_MATCHER = new AnnotationMatcher("@org.springframework.context.annotation.Bean");

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);

            if (m.getAllAnnotations().stream().anyMatch(BEAN_ANNOTATION_MATCHER::matches)) {
                // remove public modifier and copy any associated comments to the method
                doAfterVisit(new ChangeMethodAccessLevelVisitor<>(new MethodMatcher(method), null));
            }

            return m;
        }
    }
}

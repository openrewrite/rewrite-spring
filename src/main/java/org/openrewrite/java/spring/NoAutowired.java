/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicBoolean;

public class NoAutowired extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove `@Autowired`";
    }

    @Override
    public String getDescription() {
        return "Removes `@Autowired` annotation from method declarations.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.beans.factory.annotation.Autowired");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new NoAutowiredAnnotationsVisitor();
    }

    private static class NoAutowiredAnnotationsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String AUTOWIRED_CLASS = "org.springframework.beans.factory.annotation.Autowired";
        private static final AnnotationMatcher AUTOWIRED_ANNOTATION_MATCHER = new AnnotationMatcher("@" + AUTOWIRED_CLASS);

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
            AtomicBoolean foundChange = new AtomicBoolean(false);
            m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), a -> {
                        if (AUTOWIRED_ANNOTATION_MATCHER.matches(a)) {
                            foundChange.getAndSet(true);
                            return null;
                        }
                        return a;
                    }
            ));
            if (foundChange.get()) {
                maybeRemoveImport(AUTOWIRED_CLASS);
            }
            return m;
        }
    }
}


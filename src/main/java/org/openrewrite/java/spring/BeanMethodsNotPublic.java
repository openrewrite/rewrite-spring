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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.J;

import java.util.List;

public class BeanMethodsNotPublic extends Recipe {

    private final AnnotationMatcher beanAnnotationMatcher = new AnnotationMatcher("@org.springframework.context.annotation.Bean");

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new BeanMethodsNotPublicVisitor();
    }

    private class BeanMethodsNotPublicVisitor extends JavaIsoVisitor<ExecutionContext> {
        public BeanMethodsNotPublicVisitor() {
            setCursoringOn();
        }

        @Override
        public J.MethodDecl visitMethod(J.MethodDecl method, ExecutionContext executionContext) {
            J.MethodDecl m = super.visitMethod(method, executionContext);
            if (m.getAnnotations().stream().noneMatch(beanAnnotationMatcher::matches)) {
                return m;
            }

            List<J.Modifier> nonPublicModifiers = ListUtils.map(m.getModifiers(), a -> a.getType() == J.Modifier.Type.Public ? null : a);
            if (m.getModifiers() != nonPublicModifiers) {
                m = m.withModifiers(nonPublicModifiers);
                m = (J.MethodDecl) new AutoFormatVisitor<>().visit(m, executionContext, getCursor().getParent());
            }

            return m;
        }
    }
}


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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

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

            if (m.getAllAnnotations().stream().anyMatch(BEAN_ANNOTATION_MATCHER::matches) && !methodIsAnOverride(method)) {
                // remove public modifier and copy any associated comments to the method
                doAfterVisit(new ChangeMethodAccessLevelVisitor<>(new MethodMatcher(method), null));
            }

            return m;
        }

        private boolean methodIsAnOverride(J.MethodDeclaration m) {
            if(m.getType() == null || m.getType().getGenericSignature() == null) {
                // When missing type information, be conservative and make no changes
                return true;
            }
            JavaType.FullyQualified dt = m.getType().getDeclaringType();
            List<JavaType> argTypes = m.getType().getGenericSignature().getParamTypes();
            return declaresMethod(dt.getSupertype(), m.getSimpleName(), argTypes)
                    || dt.getInterfaces().stream().anyMatch(i -> declaresMethod(i, m.getSimpleName(), argTypes));
        }

        private boolean methodHasSignature(JavaType.Method m, String name, List<JavaType> argTypes) {
            if(!name.equals(m.getName())) {
                return false;
            }
            if(m.getGenericSignature() == null) {
                return false;
            }
            List<JavaType> mArgs = m.getGenericSignature().getParamTypes();
            if(mArgs.size() != argTypes.size()) {
                return false;
            }
            for(int i = 0; i < mArgs.size(); i++) {
                if(!TypeUtils.isOfType(mArgs.get(i), argTypes.get(i))) {
                    return false;
                }
            }

            return true;
        }

        private boolean declaresMethod(@Nullable JavaType.FullyQualified clazz, String name, List<JavaType> argTypes) {
            if(clazz == null) {
                return false;
            }
            if(clazz.getMethods().stream().anyMatch(m -> methodHasSignature(m, name, argTypes))) {
                return true;
            }
            JavaType.FullyQualified supertype = clazz.getSupertype();
            if(declaresMethod(supertype, name, argTypes)) {
                return true;
            }

            for(JavaType.FullyQualified i : clazz.getInterfaces()) {
                if(declaresMethod(i, name, argTypes)) {
                    return true;
                }
            }

            return false;
        }
    }
}

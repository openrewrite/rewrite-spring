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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BeanMethodsNotPublic extends Recipe {

    private final AnnotationMatcher beanAnnotationMatcher = new AnnotationMatcher("@org.springframework.context.annotation.Bean");

    private class BeanMethodsNotPublicRecipe extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodDecl visitMethod(J.MethodDecl method, ExecutionContext executionContext) {
            J.MethodDecl m = super.visitMethod(method, executionContext);
            if (m.getAnnotations().isEmpty()) {
                return m;
            }

            List<J.Annotation> beanAnnotations = m.getAnnotations()
                    .stream().filter(beanAnnotationMatcher::matches)
                    .collect(Collectors.toList());
            if (!beanAnnotations.isEmpty()) {
                J.Modifier publicMod = null;
                int publicModIndex = 0;

                for (int i = 0; i < m.getModifiers().size(); i++) {
                    if (m.getModifiers().get(i).getType() == J.Modifier.Type.Public) {
                        publicMod = m.getModifiers().get(i);
                        publicModIndex = i;
                        break;
                    }
                }

                if (publicMod != null) {
                    List<J.Modifier> modifiers = new ArrayList<>(m.getModifiers());

                    if (publicModIndex == 0 && modifiers.size() > 1) {
                        modifiers.set(1, modifiers.get(1).withPrefix(publicMod.getPrefix()));
                    }

                    modifiers.remove(publicMod);

                    m = m.withModifiers(modifiers);

                    if (modifiers.isEmpty()) {
                        if (m.getTypeParameters() != null) {
                            m.withTypeParameters(m.getTypeParameters().withBefore(publicMod.getPrefix()));
                        } else {
                            m = m.withReturnTypeExpr(m.getReturnTypeExpr().withPrefix(publicMod.getPrefix()));
                        }
                    }
                }
            }
            return m;
        }
    }
}


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
import org.openrewrite.TreeProcessor;
import org.openrewrite.java.JavaIsoProcessor;
import org.openrewrite.java.search.FindAnnotation;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BeanMethodsNotPublic extends Recipe {

    public BeanMethodsNotPublic() {
        doNext(new FindAnnotation("@org.springframework.context.annotation.Bean"));
        doNext(new BeanMethodsNotPublicRecipe());
    }

    private class BeanMethodsNotPublicRecipe extends Recipe {

        @Override
        protected TreeProcessor<?, ExecutionContext> getProcessor() {
            return new BeanMethodsNotPublicProcessor();
        }

        private class BeanMethodsNotPublicProcessor extends JavaIsoProcessor<ExecutionContext> {
            @Override
            public J.MethodDecl visitMethod(J.MethodDecl method, ExecutionContext executionContext) {
                J.MethodDecl m = super.visitMethod(method, executionContext);
                if (m.getAnnotations() == null || m.getAnnotations().isEmpty()) {
                    return m;
                }

                List<J.Annotation> autowireds = m.getAnnotations()
                        .stream().filter(a -> a.getMarkers() != null)
                        .collect(Collectors.toList());
                if (!autowireds.isEmpty()) {
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

}

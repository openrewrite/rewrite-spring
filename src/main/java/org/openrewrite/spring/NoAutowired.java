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
package org.openrewrite.spring;

import org.openrewrite.Formatting;
import org.openrewrite.AutoConfigure;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

@AutoConfigure
public class NoAutowired extends JavaRefactorVisitor {

    @Override
    public J visitMethod(J.MethodDecl method) {
        J.MethodDecl m = refactor(method, super::visitMethod);

        List<J.Annotation> autowireds = method.findAnnotations("@org.springframework.beans.factory.annotation.Autowired");
        if (method.isConstructor() && !autowireds.isEmpty()) {
            J.Annotation autowired = autowireds.iterator().next();

            List<J.Annotation> annotations = new ArrayList<>(m.getAnnotations());
            String autowiredPrefix = autowired.getFormatting().getPrefix();

            if(annotations.get(0) == autowired && annotations.size() > 1) {
                annotations.set(1, annotations.get(1).withPrefix(autowiredPrefix));
            }
            else if(!m.getModifiers().isEmpty()) {
                m = m.withModifiers(Formatting.formatFirstPrefix(m.getModifiers(), autowiredPrefix));
            }
            else if(m.getTypeParameters() != null) {
                m = m.withTypeParameters(m.getTypeParameters().withPrefix(autowiredPrefix));
            }
            else {
                m = m.withName(m.getName().withPrefix(autowiredPrefix));
            }

            annotations.remove(autowired);

            m = m.withAnnotations(annotations);
        }

        return m;
    }
}

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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.format.AutoFormat;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NoAutowired extends Recipe {

    private final AutoFormat autoFormat = new AutoFormat();

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new NoAutowiredAnnotationsVisitor();
    }

    private class NoAutowiredAnnotationsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final AnnotationMatcher annotationMatcher = new AnnotationMatcher("@org.springframework.beans.factory.annotation.Autowired");
        @Override
        public J.MethodDecl visitMethod(J.MethodDecl method, ExecutionContext executionContext) {
            J.MethodDecl m = super.visitMethod(method, executionContext);
            if (m.getAnnotations().isEmpty()) {
                return m;
            }
            List<J.Annotation> autowireds = m.getAnnotations()
                    .stream().filter(annotationMatcher::matches)
                    .collect(Collectors.toList());
            if (method.isConstructor() && !autowireds.isEmpty()) {
                J.Annotation autowired = autowireds.iterator().next();

                List<J.Annotation> annotations = new ArrayList<>(m.getAnnotations());
                Space autowiredPrefix = autowired.getPrefix();

                if (annotations.get(0) == autowired && annotations.size() > 1) {
                    annotations.set(1, annotations.get(1).withPrefix(autowiredPrefix));
                } else if (!m.getModifiers().isEmpty()) {
                    m.getModifiers().get(0).withPrefix(autowiredPrefix);
                } else if (m.getTypeParameters() != null) {
                    m = m.withTypeParameters(m.getTypeParameters().withBefore(autowiredPrefix));
                } else {
                    m = m.withName(m.getName().withPrefix(autowiredPrefix));
                }

                annotations.remove(autowired);
                m = m.withAnnotations(annotations);
                doAfterVisit(autoFormat);
            }
            return m;
        }
    }
}


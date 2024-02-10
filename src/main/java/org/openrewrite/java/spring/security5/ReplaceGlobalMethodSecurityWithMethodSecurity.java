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
package org.openrewrite.java.spring.security5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.RemoveMethodInvocationsVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.stream.Collectors;

public class ReplaceGlobalMethodSecurityWithMethodSecurity extends Recipe {
    private static final AnnotationMatcher ENABLE_GLOBAL_METHOD_SECURITY_MATCHER =
            new AnnotationMatcher("@org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity");

    private static final String EnableGlobalMethodSecurityFqn = "org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity";
    private static final String EnableMethodSecurityFqn = "org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity";

    @Override
    public String getDisplayName() {
        return "Replace global method security with method security";
    }

    @Override
    public String getDescription() {
        return "`@EnableGlobalMethodSecurity` and `<global-method-security>` are deprecated in favor of " +
               "`@EnableMethodSecurity` and `<method-security>`, respectively. The new annotation and XML " +
               "element activate Spring’s pre-post annotations by default and use AuthorizationManager internally.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        annotation = super.visitAnnotation(annotation, ctx);
                        if (ENABLE_GLOBAL_METHOD_SECURITY_MATCHER.matches(annotation)) {
                            maybeAddImport(EnableMethodSecurityFqn);
                            maybeRemoveImport(EnableGlobalMethodSecurityFqn);
                            J.Annotation replacementAnnotation = JavaTemplate.builder("@EnableMethodSecurity(prePostEnabled = false)")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "spring-security-config-5.8.+"))
                                    .imports(EnableMethodSecurityFqn)
                                    .build()
                                    .apply(getCursor(), annotation.getCoordinates().replace());

                            List<Expression> oldArgs = annotation.getArguments();
                            if (oldArgs == null || oldArgs.isEmpty()) {
                                return replacementAnnotation;
                            }

                            List<Expression> newArgs = oldArgs;
                            if (oldArgs.stream().noneMatch(this::hasPrePostEnabled)) {
                                newArgs.add(replacementAnnotation.getArguments().get(0));
                            } else {
                                newArgs = oldArgs.stream().filter(arg -> !hasPrePostEnabled(arg)).collect(Collectors.toList());
                            }
                            return autoFormat(replacementAnnotation.withArguments(newArgs), ctx);
                        }
                        return annotation;
                    }

                    private boolean hasPrePostEnabled(Expression arg) {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            return ((J.Identifier) assignment.getVariable()).getSimpleName().equals("prePostEnabled") &&
                                   RemoveMethodInvocationsVisitor.isTrue(assignment.getAssignment());
                        }
                        return false;
                    }
                }
        );
    }

}

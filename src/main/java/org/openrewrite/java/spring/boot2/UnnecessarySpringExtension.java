/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

import static java.util.Collections.singletonList;

public class UnnecessarySpringExtension extends Recipe {

    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String SPRING_EXTENSION = "org.springframework.test.context.junit.jupiter.SpringExtension";
    private static final String EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN = String.format("@%s(%s.class)", EXTEND_WITH, SPRING_EXTENSION);
    private static final AnnotationMatcher EXTENDS_WITH_SPRING_EXACT_MATCHER = new AnnotationMatcher(EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN, false);

    @Override
    public String getDisplayName() {
        return "Remove `@SpringExtension`";
    }

    @Override
    public String getDescription() {
        return "`@SpringBootTest` and all test slice annotations already applies `@SpringExtension` as of Spring Boot 2.1.0.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(SPRING_EXTENSION, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        Set<J.Annotation> extendsWithMetaAnnotations = FindAnnotations.find(classDecl, EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN, true);
                        if (1 < extendsWithMetaAnnotations.size()) {
                            return classDecl.withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(), annotation -> {
                                if (EXTENDS_WITH_SPRING_EXACT_MATCHER.matches(annotation)) {
                                    Expression expression = annotation.getArguments().get(0);
                                    if (expression instanceof J.FieldAccess) {
                                        doAfterVisit(new RemoveAnnotationVisitor(EXTENDS_WITH_SPRING_EXACT_MATCHER));
                                        maybeRemoveImport(SPRING_EXTENSION);
                                    } else if (expression instanceof J.NewArray &&
                                            ((J.NewArray) expression).getInitializer() != null) {
                                        if (((J.NewArray) expression).getInitializer().size() == 1) {
                                            doAfterVisit(new RemoveAnnotationVisitor(EXTENDS_WITH_SPRING_EXACT_MATCHER));
                                            maybeRemoveImport(SPRING_EXTENSION);
                                        } else {
                                            maybeRemoveImport(EXTEND_WITH);
                                            maybeRemoveImport(SPRING_EXTENSION);
                                            J.Annotation newAnnotation = annotation.withArguments(singletonList(((J.NewArray) expression)
                                                    .withInitializer(ListUtils.map(((J.NewArray) expression).getInitializer(),
                                                            e -> TypeUtils.isAssignableTo("java.lang.Class<" + SPRING_EXTENSION + ">", e.getType()) ? null : e))));
                                            return maybeAutoFormat(annotation, newAnnotation, ctx);
                                        }
                                    }
                                }
                                return annotation;
                            }));
                        }
                        return super.visitClassDeclaration(classDecl, ctx);
                    }
                });
    }
}

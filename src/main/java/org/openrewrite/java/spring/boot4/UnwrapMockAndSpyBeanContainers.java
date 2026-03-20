/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.boot4;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.openrewrite.Tree.randomId;

public class UnwrapMockAndSpyBeanContainers extends Recipe {

    private static final String MOCK_BEANS_FQN = "org.springframework.boot.test.mock.mockito.MockBeans";
    private static final String SPY_BEANS_FQN = "org.springframework.boot.test.mock.mockito.SpyBeans";

    private static final AnnotationMatcher MOCK_BEANS_MATCHER = new AnnotationMatcher("@" + MOCK_BEANS_FQN);
    private static final AnnotationMatcher SPY_BEANS_MATCHER = new AnnotationMatcher("@" + SPY_BEANS_FQN);

    @Getter
    final String displayName = "Unwrap `@MockBeans` and `@SpyBeans` container annotations";

    @Getter
    final String description = "Replaces class-level `@MockBeans` and `@SpyBeans` container annotations " +
            "with individual class-level `@MockBean` and `@SpyBean` annotations, renaming the " +
            "`value`/`classes` attribute to `types` for compatibility with `@MockitoBean`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(MOCK_BEANS_FQN, false),
                        new UsesType<>(SPY_BEANS_FQN, false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        List<J.Annotation> newAnnotations = new ArrayList<>();
                        boolean changed = false;

                        for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                            boolean isMockBeans = MOCK_BEANS_MATCHER.matches(annotation);
                            boolean isSpyBeans = SPY_BEANS_MATCHER.matches(annotation);
                            if (!isMockBeans && !isSpyBeans) {
                                newAnnotations.add(annotation);
                                continue;
                            }

                            String containerFqn = isMockBeans ? MOCK_BEANS_FQN : SPY_BEANS_FQN;
                            List<J.Annotation> innerAnnotations = extractInnerAnnotations(annotation);
                            if (innerAnnotations.isEmpty()) {
                                newAnnotations.add(annotation);
                                continue;
                            }

                            changed = true;
                            maybeRemoveImport(containerFqn);

                            for (J.Annotation inner : innerAnnotations) {
                                inner = renameClassAttributeToTypes(inner);
                                inner = inner.withPrefix(annotation.getPrefix());
                                newAnnotations.add(inner);
                            }
                        }

                        if (changed) {
                            cd = cd.withLeadingAnnotations(newAnnotations);
                        }

                        return cd;
                    }
                }
        );
    }

    private static List<J.Annotation> extractInnerAnnotations(J.Annotation containerAnnotation) {
        List<J.Annotation> annotations = new ArrayList<>();
        List<Expression> args = containerAnnotation.getArguments();
        if (args == null || args.isEmpty()) {
            return annotations;
        }

        for (Expression arg : args) {
            if (arg instanceof J.NewArray) {
                // @MockBeans({@MockBean(A.class), @MockBean(B.class)})
                J.NewArray newArray = (J.NewArray) arg;
                if (newArray.getInitializer() != null) {
                    for (Expression init : newArray.getInitializer()) {
                        if (init instanceof J.Annotation) {
                            annotations.add((J.Annotation) init);
                        }
                    }
                }
            } else if (arg instanceof J.Annotation) {
                // @MockBeans(@MockBean(A.class))
                annotations.add((J.Annotation) arg);
            } else if (arg instanceof J.Assignment) {
                // @MockBeans(value = {...})
                J.Assignment assignment = (J.Assignment) arg;
                Expression value = assignment.getAssignment();
                if (value instanceof J.NewArray) {
                    J.NewArray newArray = (J.NewArray) value;
                    if (newArray.getInitializer() != null) {
                        for (Expression init : newArray.getInitializer()) {
                            if (init instanceof J.Annotation) {
                                annotations.add((J.Annotation) init);
                            }
                        }
                    }
                } else if (value instanceof J.Annotation) {
                    annotations.add((J.Annotation) value);
                }
            }
        }
        return annotations;
    }

    private static J.Annotation renameClassAttributeToTypes(J.Annotation annotation) {
        if (annotation.getArguments() == null || annotation.getArguments().isEmpty()) {
            return annotation;
        }

        List<Expression> newArgs = new ArrayList<>();
        boolean modified = false;

        for (Expression arg : annotation.getArguments()) {
            if (arg instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) arg;
                if (assignment.getVariable() instanceof J.Identifier) {
                    String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                    if ("value".equals(name) || "classes".equals(name)) {
                        newArgs.add(assignment.withVariable(
                                ((J.Identifier) assignment.getVariable()).withSimpleName("types")
                        ));
                        modified = true;
                        continue;
                    }
                }
                newArgs.add(arg);
            } else if (isClassLiteralOrArray(arg)) {
                // Implicit value: @MockBean(ClassA.class) or @MockBean({A.class, B.class})
                newArgs.add(wrapInTypesAssignment(arg));
                modified = true;
            } else {
                newArgs.add(arg);
            }
        }

        return modified ? annotation.withArguments(newArgs) : annotation;
    }

    private static boolean isClassLiteralOrArray(Expression expr) {
        if (expr instanceof J.FieldAccess && "class".equals(((J.FieldAccess) expr).getSimpleName())) {
            return true;
        }
        return expr instanceof J.NewArray;
    }

    private static J.Assignment wrapInTypesAssignment(Expression value) {
        J.Identifier typesIdent = new J.Identifier(
                randomId(), Space.EMPTY, Markers.EMPTY,
                Collections.emptyList(), "types", null, null
        );
        Expression rhs = value.withPrefix(Space.format(" "));
        JLeftPadded<Expression> paddedRhs = JLeftPadded.build(rhs).withBefore(Space.format(" "));
        return new J.Assignment(
                randomId(),
                value.getPrefix(),
                Markers.EMPTY,
                typesIdent,
                paddedRhs,
                null
        );
    }
}

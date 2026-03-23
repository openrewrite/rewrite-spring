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

import org.openrewrite.internal.ListUtils;

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
            "with a single class-level `@MockBean` or `@SpyBean` annotation with a merged `types` " +
            "attribute for compatibility with `@MockitoBean`.";

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
                        return cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), annotation -> {
                            boolean isMockBeans = MOCK_BEANS_MATCHER.matches(annotation);
                            boolean isSpyBeans = SPY_BEANS_MATCHER.matches(annotation);
                            if (!isMockBeans && !isSpyBeans) {
                                return annotation;
                            }

                            List<J.Annotation> innerAnnotations = extractInnerAnnotations(annotation);
                            if (innerAnnotations.isEmpty()) {
                                return annotation;
                            }

                            maybeRemoveImport(isMockBeans ? MOCK_BEANS_FQN : SPY_BEANS_FQN);

                            List<Expression> allTypes = new ArrayList<>();
                            for (J.Annotation inner : innerAnnotations) {
                                allTypes.addAll(extractTypeExpressions(inner));
                            }

                            return innerAnnotations.get(0)
                                    .withPrefix(annotation.getPrefix())
                                    .withArguments(Collections.singletonList(
                                            createTypesAssignment(allTypes)));
                        }));
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

    private static List<Expression> extractTypeExpressions(J.Annotation annotation) {
        List<Expression> types = new ArrayList<>();
        if (annotation.getArguments() == null || annotation.getArguments().isEmpty()) {
            return types;
        }

        for (Expression arg : annotation.getArguments()) {
            if (arg instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) arg;
                if (assignment.getVariable() instanceof J.Identifier) {
                    String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                    if ("value".equals(name) || "classes".equals(name)) {
                        Expression value = assignment.getAssignment();
                        if (value instanceof J.NewArray && ((J.NewArray) value).getInitializer() != null) {
                            types.addAll(((J.NewArray) value).getInitializer());
                        } else {
                            types.add(value);
                        }
                    }
                }
            } else if (arg instanceof J.FieldAccess && "class".equals(((J.FieldAccess) arg).getSimpleName())) {
                // Implicit value: @MockBean(ClassA.class)
                types.add(arg);
            } else if (arg instanceof J.NewArray && ((J.NewArray) arg).getInitializer() != null) {
                // Implicit value: @MockBean({A.class, B.class})
                types.addAll(((J.NewArray) arg).getInitializer());
            }
        }
        return types;
    }

    private static J.Assignment createTypesAssignment(List<Expression> typeExpressions) {
        J.Identifier typesIdent = new J.Identifier(
                randomId(), Space.EMPTY, Markers.EMPTY,
                Collections.emptyList(), "types", null, null
        );

        Expression rhs;
        if (typeExpressions.size() == 1) {
            rhs = typeExpressions.get(0).withPrefix(Space.format(" "));
        } else {
            // Format as {A.class, B.class}
            List<JRightPadded<Expression>> padded = new ArrayList<>();
            for (int i = 0; i < typeExpressions.size(); i++) {
                Expression expr = typeExpressions.get(i);
                if (i == 0) {
                    expr = expr.withPrefix(Space.EMPTY);
                } else {
                    expr = expr.withPrefix(Space.format(" "));
                }
                padded.add(JRightPadded.build(expr));
            }
            rhs = new J.NewArray(
                    randomId(), Space.format(" "), Markers.EMPTY,
                    null, Collections.emptyList(),
                    JContainer.build(Space.EMPTY, padded, Markers.EMPTY),
                    null
            );
        }

        JLeftPadded<Expression> paddedRhs = JLeftPadded.build(rhs).withBefore(Space.format(" "));
        return new J.Assignment(
                randomId(), Space.EMPTY, Markers.EMPTY,
                typesIdent, paddedRhs, null
        );
    }
}

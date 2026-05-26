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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.marker.AnnotationConstructor;
import org.openrewrite.kotlin.marker.Modifier;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
                        boolean isKotlin = getCursor().firstEnclosing(K.CompilationUnit.class) != null;
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

                            J.Annotation lifted = innerAnnotations.get(0);
                            if (isKotlin) {
                                // Inner annotations in Kotlin containers carry markers that suppress the
                                // leading `@`; strip them so the lifted annotation prints with `@`.
                                Markers m = lifted.getMarkers();
                                m = m.removeByType(AnnotationConstructor.class);
                                m = m.removeByType(Modifier.class);
                                lifted = lifted.withMarkers(m);
                            }
                            return lifted
                                    .withPrefix(annotation.getPrefix())
                                    .withArguments(singletonList(
                                            createTypesAssignment(allTypes, isKotlin)));
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
            collectAnnotationArgument(arg, annotations);
        }
        return annotations;
    }

    private static void collectAnnotationArgument(Expression arg, List<J.Annotation> annotations) {
        if (arg instanceof J.NewArray) {
            // Java: @MockBeans({@MockBean(A.class), @MockBean(B.class)})
            J.NewArray newArray = (J.NewArray) arg;
            if (newArray.getInitializer() != null) {
                for (Expression init : newArray.getInitializer()) {
                    collectAnnotationArgument(init, annotations);
                }
            }
        } else if (arg instanceof K.ListLiteral) {
            // Kotlin: @MockBeans([MockBean(A::class), MockBean(B::class)])
            for (Expression element : ((K.ListLiteral) arg).getElements()) {
                collectAnnotationArgument(element, annotations);
            }
        } else if (arg instanceof J.Annotation) {
            // Java single-arg: @MockBeans(@MockBean(A.class))
            // Kotlin variadic:  @MockBeans(MockBean(A::class), MockBean(B::class))
            annotations.add((J.Annotation) arg);
        } else if (arg instanceof J.Assignment) {
            // @MockBeans(value = ...)
            J.Assignment assignment = (J.Assignment) arg;
            collectAnnotationArgument(assignment.getAssignment(), annotations);
        }
    }

    private static List<Expression> extractTypeExpressions(J.Annotation annotation) {
        List<Expression> types = new ArrayList<>();
        if (annotation.getArguments() == null || annotation.getArguments().isEmpty()) {
            return types;
        }

        for (Expression arg : annotation.getArguments()) {
            collectTypeExpression(arg, types);
        }
        return types;
    }

    private static void collectTypeExpression(Expression arg, List<Expression> types) {
        if (arg instanceof J.Assignment) {
            J.Assignment assignment = (J.Assignment) arg;
            if (assignment.getVariable() instanceof J.Identifier) {
                String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                if ("value".equals(name) || "classes".equals(name) || "types".equals(name)) {
                    collectTypeExpression(assignment.getAssignment(), types);
                }
            }
        } else if (arg instanceof J.NewArray && ((J.NewArray) arg).getInitializer() != null) {
            // Java: {A.class, B.class}
            for (Expression init : ((J.NewArray) arg).getInitializer()) {
                collectTypeExpression(init, types);
            }
        } else if (arg instanceof K.ListLiteral) {
            // Kotlin: [A::class, B::class]
            for (Expression element : ((K.ListLiteral) arg).getElements()) {
                collectTypeExpression(element, types);
            }
        } else if (arg instanceof J.FieldAccess && "class".equals(((J.FieldAccess) arg).getSimpleName())) {
            // Java: A.class
            types.add(arg);
        } else if (arg instanceof J.MemberReference && "class".equals(((J.MemberReference) arg).getReference().getSimpleName())) {
            // Kotlin: A::class
            types.add(arg);
        }
    }

    private static J.Assignment createTypesAssignment(List<Expression> typeExpressions, boolean kotlin) {
        J.Identifier typesIdent = new J.Identifier(
                randomId(), Space.EMPTY, Markers.EMPTY,
                emptyList(), "types", null, null
        );

        Expression rhs;
        if (kotlin) {
            // Format as [A::class, B::class]
            List<JRightPadded<Expression>> padded = new ArrayList<>();
            for (int i = 0; i < typeExpressions.size(); i++) {
                Expression expr = typeExpressions.get(i);
                expr = expr.withPrefix(i == 0 ? Space.EMPTY : Space.format(" "));
                padded.add(JRightPadded.build(expr));
            }
            rhs = new K.ListLiteral(
                    randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                    JContainer.build(Space.EMPTY, padded, Markers.EMPTY),
                    null
            );
        } else if (typeExpressions.size() == 1) {
            // Java single-element shorthand: types = Foo.class
            rhs = typeExpressions.get(0).withPrefix(Space.SINGLE_SPACE);
        } else {
            // Format as {A.class, B.class}
            List<JRightPadded<Expression>> padded = new ArrayList<>();
            for (int i = 0; i < typeExpressions.size(); i++) {
                Expression expr = typeExpressions.get(i);
                expr = expr.withPrefix(i == 0 ? Space.EMPTY : Space.format(" "));
                padded.add(JRightPadded.build(expr));
            }
            rhs = new J.NewArray(
                    randomId(), Space.format(" "), Markers.EMPTY,
                    null, emptyList(),
                    JContainer.build(Space.EMPTY, padded, Markers.EMPTY),
                    null
            );
        }

        JLeftPadded<Expression> paddedRhs = JLeftPadded.build(rhs).withBefore(Space.SINGLE_SPACE);
        return new J.Assignment(
                randomId(), Space.EMPTY, Markers.EMPTY,
                typesIdent, paddedRhs, null
        );
    }
}

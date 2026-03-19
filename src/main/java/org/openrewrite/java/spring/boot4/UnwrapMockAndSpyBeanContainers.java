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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class UnwrapMockAndSpyBeanContainers extends Recipe {

    private static final String MOCK_BEANS_FQN = "org.springframework.boot.test.mock.mockito.MockBeans";
    private static final String SPY_BEANS_FQN = "org.springframework.boot.test.mock.mockito.SpyBeans";
    private static final String MOCK_BEAN_FQN = "org.springframework.boot.test.mock.mockito.MockBean";
    private static final String SPY_BEAN_FQN = "org.springframework.boot.test.mock.mockito.SpyBean";

    private static final AnnotationMatcher MOCK_BEANS_MATCHER = new AnnotationMatcher("@" + MOCK_BEANS_FQN);
    private static final AnnotationMatcher SPY_BEANS_MATCHER = new AnnotationMatcher("@" + SPY_BEANS_FQN);

    @Getter
    final String displayName = "Unwrap `@MockBeans` and `@SpyBeans` container annotations";

    @Getter
    final String description = "Replaces class-level `@MockBeans` and `@SpyBeans` container annotations " +
            "with individual field-level `@MockBean` and `@SpyBean` annotations. " +
            "The class type specified in the `value` or `classes` attribute becomes the field type.";

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

                        for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                            boolean isMockBeans = MOCK_BEANS_MATCHER.matches(annotation);
                            boolean isSpyBeans = SPY_BEANS_MATCHER.matches(annotation);
                            if (!isMockBeans && !isSpyBeans) {
                                continue;
                            }

                            String innerAnnotationName = isMockBeans ? "MockBean" : "SpyBean";
                            String innerAnnotationFqn = isMockBeans ? MOCK_BEAN_FQN : SPY_BEAN_FQN;
                            String containerFqn = isMockBeans ? MOCK_BEANS_FQN : SPY_BEANS_FQN;

                            List<TypeInfo> typesToMock = extractTypesFromContainer(annotation);
                            if (typesToMock.isEmpty()) {
                                continue;
                            }

                            // Remove the container annotation
                            cd = cd.withLeadingAnnotations(
                                    cd.getLeadingAnnotations().stream()
                                            .filter(a -> a != annotation)
                                            .collect(java.util.stream.Collectors.toList())
                            );
                            maybeRemoveImport(containerFqn);

                            // Add individual field-level annotations
                            for (TypeInfo typeInfo : typesToMock) {
                                String fieldName = decapitalize(typeInfo.simpleName);
                                String template = "@" + innerAnnotationName + "\nprivate " + typeInfo.simpleName + " " + fieldName + ";";

                                cd = JavaTemplate.builder(template)
                                        .javaParser(JavaParser.fromJavaVersion()
                                                .classpathFromResources(ctx, "spring-boot-test-3")
                                                .dependsOn("package " + typeInfo.packageName + "; public class " + typeInfo.simpleName + " {}"))
                                        .imports(innerAnnotationFqn)
                                        .build()
                                        .apply(updateCursor(cd), cd.getBody().getCoordinates().lastStatement());

                                maybeAddImport(typeInfo.fqn);
                            }

                            maybeAddImport(innerAnnotationFqn);
                        }

                        return cd;
                    }
                }
        );
    }

    private static List<TypeInfo> extractTypesFromContainer(J.Annotation containerAnnotation) {
        List<TypeInfo> types = new ArrayList<>();
        List<Expression> args = containerAnnotation.getArguments();
        if (args == null || args.isEmpty()) {
            return types;
        }

        for (Expression arg : args) {
            // Handle @MockBeans({@MockBean(A.class), @MockBean(B.class)}) - NewArray with annotations
            if (arg instanceof J.NewArray) {
                J.NewArray newArray = (J.NewArray) arg;
                if (newArray.getInitializer() != null) {
                    for (Expression init : newArray.getInitializer()) {
                        if (init instanceof J.Annotation) {
                            extractTypeFromInnerAnnotation((J.Annotation) init, types);
                        }
                    }
                }
            }
            // Handle @MockBeans(@MockBean(A.class)) - single annotation (no array)
            else if (arg instanceof J.Annotation) {
                extractTypeFromInnerAnnotation((J.Annotation) arg, types);
            }
            // Handle value = {...} assignment
            else if (arg instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) arg;
                Expression value = assignment.getAssignment();
                if (value instanceof J.NewArray) {
                    J.NewArray newArray = (J.NewArray) value;
                    if (newArray.getInitializer() != null) {
                        for (Expression init : newArray.getInitializer()) {
                            if (init instanceof J.Annotation) {
                                extractTypeFromInnerAnnotation((J.Annotation) init, types);
                            }
                        }
                    }
                } else if (value instanceof J.Annotation) {
                    extractTypeFromInnerAnnotation((J.Annotation) value, types);
                }
            }
        }
        return types;
    }

    private static void extractTypeFromInnerAnnotation(J.Annotation innerAnnotation, List<TypeInfo> types) {
        List<Expression> args = innerAnnotation.getArguments();
        if (args == null || args.isEmpty()) {
            return;
        }

        for (Expression arg : args) {
            TypeInfo typeInfo = extractClassLiteral(arg);
            if (typeInfo != null) {
                types.add(typeInfo);
                return;
            }

            // Handle named attributes: value = X.class or classes = X.class
            if (arg instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) arg;
                if (assignment.getVariable() instanceof J.Identifier) {
                    String attrName = ((J.Identifier) assignment.getVariable()).getSimpleName();
                    if ("value".equals(attrName) || "classes".equals(attrName)) {
                        Expression value = assignment.getAssignment();
                        typeInfo = extractClassLiteral(value);
                        if (typeInfo != null) {
                            types.add(typeInfo);
                            return;
                        }
                        // Handle array: classes = {A.class, B.class}
                        if (value instanceof J.NewArray) {
                            J.NewArray newArray = (J.NewArray) value;
                            if (newArray.getInitializer() != null) {
                                for (Expression init : newArray.getInitializer()) {
                                    typeInfo = extractClassLiteral(init);
                                    if (typeInfo != null) {
                                        types.add(typeInfo);
                                    }
                                }
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    private static @Nullable TypeInfo extractClassLiteral(Expression expr) {
        if (expr instanceof J.FieldAccess) {
            J.FieldAccess fieldAccess = (J.FieldAccess) expr;
            if ("class".equals(fieldAccess.getSimpleName())) {
                JavaType type = fieldAccess.getTarget().getType();
                if (type instanceof JavaType.FullyQualified) {
                    JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
                    return new TypeInfo(fq.getFullyQualifiedName(), fq.getClassName(), fq.getPackageName());
                }
                // Fallback for unresolved types: use the source text
                String targetText = fieldAccess.getTarget().toString().trim();
                int lastDot = targetText.lastIndexOf('.');
                if (lastDot > 0) {
                    return new TypeInfo(targetText, targetText.substring(lastDot + 1), targetText.substring(0, lastDot));
                }
                return new TypeInfo("java.lang." + targetText, targetText, "java.lang");
            }
        }
        return null;
    }

    private static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static class TypeInfo {
        final String fqn;
        final String simpleName;
        final String packageName;

        TypeInfo(String fqn, String simpleName, String packageName) {
            this.fqn = fqn;
            this.simpleName = simpleName;
            this.packageName = packageName;
        }
    }
}

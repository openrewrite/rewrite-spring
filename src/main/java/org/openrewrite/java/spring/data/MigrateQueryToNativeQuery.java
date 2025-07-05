/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.data;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MigrateQueryToNativeQuery extends Recipe {

    private static final String DATA_JPA_QUERY_FQN = "org.springframework.data.jpa.repository.Query";
    private static final String DATA_JPA_NATIVE_QUERY_FQN = "org.springframework.data.jpa.repository.NativeQuery";

    private static final AnnotationMatcher DATA_JPA_QUERY_ANNOTATION_MATCHER =
            new AnnotationMatcher("@" + DATA_JPA_QUERY_FQN);

    private static final boolean NATIVE_QUERY_DEFAULT_VALUE = false;

    @Override
    public String getDisplayName() {
        // language=markdown
        return "Replace `@Query` annotation by `@NativeQuery` when possible";
    }

    @Override
    public String getDescription() {
        // language=markdown
        return "Replace `@Query` annotation by `@NativeQuery` when `nativeQuery = true`. " +
                "`@NativeQuery` was introduced in Spring Data JPA 3.4.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Inspiration for this recipe found in org.openrewrite.java.spring.NoRequestMappingAnnotation
        return Preconditions.check(
                new UsesType<>(DATA_JPA_QUERY_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation a = super.visitAnnotation(annotation, ctx);
                        if (DATA_JPA_QUERY_ANNOTATION_MATCHER.matches(a) && getCursor().getParentOrThrow().getValue() instanceof J.MethodDeclaration) {
                            Optional<J.Assignment> nativeQueryArg = findNativeQueryArgument(a);
                            boolean isNativeQuery = nativeQueryArg
                                    .map(assignment -> extractBooleanValue(assignment))
                                    .orElse(NATIVE_QUERY_DEFAULT_VALUE);
                            if (!isNativeQuery) {
                                return a;
                            } else {
                                maybeRemoveImport(DATA_JPA_QUERY_FQN);

                                // Remove the argument nativeQuery
                                List<Expression> retainedArgs;
                                if (nativeQueryArg.isPresent()) {
                                    retainedArgs = a.getArguments().stream()
                                            .filter(arg -> !nativeQueryArg.get().equals(arg))
                                            .collect(Collectors.toList());
                                } else {
                                    retainedArgs = a.getArguments();
                                }

                                a = a.withArguments(retainedArgs);
                            }

                            // Change the Annotation Type => call recipe ChangeType
                            maybeAddImport(DATA_JPA_NATIVE_QUERY_FQN);
                            a = (J.Annotation) new ChangeType(DATA_JPA_QUERY_FQN, DATA_JPA_NATIVE_QUERY_FQN, false)
                                    .getVisitor().visit(a, ctx, getCursor().getParentOrThrow());

                            // Apply shorthand style if there is only one remaining argument named "value"
                            if (a != null && a.getArguments() != null && a.getArguments().size() == 1) {
                                a = a.withArguments(
                                        a.getArguments().stream()
                                                .map(arg -> hasArgument("value").test(arg) ? ((J.Assignment) arg).getAssignment().withPrefix(Space.EMPTY) : arg)
                                                .collect(Collectors.toList())
                                );
                            }
                        }
                        return a != null ? a : annotation;
                    }

                    private Optional<J.Assignment> findNativeQueryArgument(J.Annotation annotation) {
                        if (annotation.getArguments() == null) {
                            return Optional.empty();
                        }
                        return annotation.getArguments().stream()
                                .filter(hasArgument("nativeQuery"))
                                .map(J.Assignment.class::cast)
                                .findFirst();
                    }

                    private Predicate<Expression> hasArgument(String argName) {
                        return arg -> arg instanceof J.Assignment &&
                                ((J.Assignment) arg).getVariable() instanceof J.Identifier &&
                                argName.equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName());
                    }

                    private @Nullable Boolean extractBooleanValue(J.@Nullable Assignment assignment) {
                        if (assignment == null) {
                            return null;
                        }

                        Expression assignedExpr = assignment.getAssignment();

                        if (assignedExpr instanceof J.Literal) {
                            J.Literal literal = (J.Literal) assignedExpr;
                            Object value = literal.getValue();

                            if (value instanceof Boolean) {
                                return (Boolean) value;
                            }
                        }

                        // If it's not a literal (e.g. a method call, variable, etc.), we can't resolve statically
                        return null;
                    }
                }
        );
    }
}

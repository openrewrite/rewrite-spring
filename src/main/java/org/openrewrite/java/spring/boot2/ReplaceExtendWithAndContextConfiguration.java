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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class ReplaceExtendWithAndContextConfiguration extends Recipe {
    private static final String FQN_EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String FQN_CONTEXT_CONFIGURATION = "org.springframework.test.context.ContextConfiguration";
    private static final String FQN_SPRING_JUNIT_CONFIG = "org.springframework.test.context.junit.jupiter.SpringJUnitConfig";

    @Getter
    final String displayName = "Replace `@ExtendWith` and `@ContextConfiguration` with `@SpringJunitConfig`";

    @Getter
    final String description = "Replaces `@ExtendWith(SpringRunner.class)` and `@ContextConfiguration` with `@SpringJunitConfig`, " +
            "preserving attributes on `@ContextConfiguration`, unless `@ContextConfiguration(loader = ...)` is used.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(new UsesType<>(FQN_EXTEND_WITH, false), new UsesType<>(FQN_CONTEXT_CONFIGURATION, false)),
                new JavaIsoVisitor<ExecutionContext>() {
                    private final AnnotationMatcher CONTEXT_CONFIGURATION_ANNOTATION_MATCHER = new AnnotationMatcher("@" + FQN_CONTEXT_CONFIGURATION, true);

                    {
                        doAfterVisit(new UnnecessarySpringExtension().getVisitor());
                    }

                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation a = super.visitAnnotation(annotation, ctx);

                        if (CONTEXT_CONFIGURATION_ANNOTATION_MATCHER.matches(a) && getCursor().getParentOrThrow().getValue() instanceof J.ClassDeclaration) {
                            // @SpringJUnitConfig supports every attribute on @ContextConfiguration except loader()
                            // If it's present, skip the transformation since removing @ContextConfiguration will do harm
                            Optional<J.Assignment> loaderArg = findLoaderArgument(a);
                            if (loaderArg.isPresent()) {
                                return a;
                            }

                            // @ContextConfiguration value() is an alias for locations()
                            // @SpringJUnitConfig value() is an alias for classes()
                            // Since these are incompatible, we need to map value() to locations()
                            // If value() is present (either explicitly or implicitly), replace it with locations()
                            // Note that it's invalid to specify both value() and locations() on @ContextConfiguration
                            if (a.getArguments() != null) {
                                List<Expression> newArgs = new CopyOnWriteArrayList<>(a.getArguments());
                                replaceValueArgumentWithLocations(a, newArgs);
                                a = a.withArguments(newArgs);
                            }

                            // Change the @ContextConfiguration annotation to @SpringJUnitConfig
                            maybeRemoveImport(FQN_CONTEXT_CONFIGURATION);
                            maybeAddImport(FQN_SPRING_JUNIT_CONFIG);
                            a = (J.Annotation) new ChangeType(FQN_CONTEXT_CONFIGURATION, FQN_SPRING_JUNIT_CONFIG, false)
                                    .getVisitor().visitNonNull(a, ctx, getCursor().getParentOrThrow());
                            a = autoFormat(a, ctx);
                        }

                        return a;
                    }

                    private void replaceValueArgumentWithLocations(J.Annotation a, List<Expression> newArgs) {
                        for (int i = 0; i < newArgs.size(); i++) {
                            Expression expression = newArgs.get(i);
                            if (expression instanceof J.Assignment) {
                                J.Assignment assignment = (J.Assignment) expression;
                                String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                                if ("value".equals(name)) {
                                    J.Assignment as = createLocationsAssignment(a, assignment.getAssignment())
                                            .withPrefix(expression.getPrefix());
                                    newArgs.set(i, as);
                                    break;
                                }
                            } else {
                                // The implicit assignment to "value"
                                J.Assignment as = createLocationsAssignment(a, expression).withPrefix(expression.getPrefix());
                                newArgs.set(i, as);
                                break;
                            }
                        }
                    }

                    private J.Assignment createLocationsAssignment(J.Annotation annotation, Expression value) {
                        return (J.Assignment) ((J.Annotation)
                            JavaTemplate.builder("locations = #{any(String)}")
                                .contextSensitive().build().apply(
                                    getCursor(),
                                    annotation.getCoordinates().replaceArguments(),
                                    value
                                )).getArguments().get(0);
                    }
                });
    }

    private static Optional<J.Assignment> findLoaderArgument(J.Annotation annotation) {
        if (annotation.getArguments() == null) {
            return Optional.empty();
        }
        return annotation.getArguments().stream()
                .filter(arg -> arg instanceof J.Assignment &&
                        ((J.Assignment) arg).getVariable() instanceof J.Identifier &&
                        "loader".equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName()))
                .map(J.Assignment.class::cast)
                .findFirst();
    }
}

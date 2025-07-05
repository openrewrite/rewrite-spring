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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationAttribute;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.time.Duration;

public class MigrateQueryToNativeQuery extends Recipe {

    private static final String DATA_JPA_QUERY_FQN = "org.springframework.data.jpa.repository.Query";
    private static final String DATA_JPA_NATIVE_QUERY_FQN = "org.springframework.data.jpa.repository.NativeQuery";
    private static final Annotated.Matcher MATCHER = new Annotated.Matcher("@" + DATA_JPA_QUERY_FQN + "(nativeQuery = true)");

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
        return Preconditions.check(
                new UsesType<>(DATA_JPA_QUERY_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation an = super.visitAnnotation(annotation, ctx);

                        if (!MATCHER.get(getCursor())
                                .filter(a -> a.getAttribute("nativeQuery")
                                        .filter(l -> "true".equals(l.getString()))
                                        .isPresent())
                                .isPresent()) {
                            return an;
                        }

                        an = (J.Annotation) new RemoveAnnotationAttribute(DATA_JPA_QUERY_FQN, "nativeQuery")
                                .getVisitor()
                                .visitNonNull(an, ctx, getCursor().getParentOrThrow());
                        an = (J.Annotation) new ChangeType(DATA_JPA_QUERY_FQN, DATA_JPA_NATIVE_QUERY_FQN, false)
                                .getVisitor().visitNonNull(an, ctx, getCursor().getParentOrThrow());
                        maybeRemoveImport(DATA_JPA_QUERY_FQN);
                        maybeAddImport(DATA_JPA_NATIVE_QUERY_FQN);
                        if (an.getArguments() != null && an.getArguments().size() == 1) {
                            an = an.withArguments(ListUtils.mapFirst(an.getArguments(), v -> {
                                if (v instanceof J.Assignment &&
                                        ((J.Assignment) v).getVariable() instanceof J.Identifier &&
                                        "value".equals(((J.Identifier) ((J.Assignment) v).getVariable()).getSimpleName())) {
                                    return ((J.Assignment) v).getAssignment().withPrefix(Space.EMPTY);
                                }
                                return v;
                            }));
                        }
                        return an;
                    }
                }
        );
    }
}

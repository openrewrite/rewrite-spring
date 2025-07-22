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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class RemoveSolrAutoConfigurationExclude extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `SolrAutoConfiguration`";
    }

    @Override
    public String getDescription() {
        return "`SolrAutoConfiguration` was removed in Spring Boot 3; remove references to it from exclusions on annotations.";
    }

    private static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String ENABLE_AUTO_CONFIGURATION = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";
    private static final AnnotationMatcher SBA_MATCHER = new AnnotationMatcher(SPRING_BOOT_APPLICATION);
    private static final AnnotationMatcher EAC_MATCHER = new AnnotationMatcher(ENABLE_AUTO_CONFIGURATION);

    private static final String SOLR_AUTO_CONFIGURATION = "SolrAutoConfiguration";
    private static final String SOLR_AUTOCONFIGURATION_FQN = "org.springframework.boot.autoconfigure.solr." + SOLR_AUTO_CONFIGURATION;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                        new UsesType<>(SPRING_BOOT_APPLICATION, true),
                        new UsesType<>(ENABLE_AUTO_CONFIGURATION, true)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation a = super.visitAnnotation(annotation, ctx);
                        if (!SBA_MATCHER.matches(a) && !EAC_MATCHER.matches(a)) {
                            return a;
                        }
                        maybeRemoveImport(SOLR_AUTOCONFIGURATION_FQN);

                        return a.withArguments(ListUtils.map(a.getArguments(), it -> {
                            if (it instanceof J.Assignment) {
                                J.Assignment as = (J.Assignment) it;
                                if (as.getAssignment() == null || !"exclude".equals(((J.Identifier) as.getVariable()).getSimpleName())) {
                                    return it;
                                }
                                if (isSolrAutoConfigurationClassReference(as.getAssignment())) {
                                    return null;
                                }
                                if (as.getAssignment() instanceof J.NewArray) {
                                    J.NewArray array = (J.NewArray) as.getAssignment();
                                    List<Expression> newInitializer = ListUtils.map(array.getInitializer(),
                                            expr -> isSolrAutoConfigurationClassReference(expr) ? null : expr);
                                    //noinspection DataFlowIssue
                                    if (newInitializer.isEmpty()) {
                                        return null;
                                    }
                                    return maybeAutoFormat(it, as.withAssignment(array.withInitializer(newInitializer)), ctx);
                                }
                            }
                            return it;
                        }));
                    }

                    private boolean isSolrAutoConfigurationClassReference(Expression expr) {
                        return expr instanceof J.FieldAccess &&
                               TypeUtils.isAssignableTo(SOLR_AUTOCONFIGURATION_FQN, ((J.FieldAccess) expr).getTarget().getType());
                    }
                });
    }
}

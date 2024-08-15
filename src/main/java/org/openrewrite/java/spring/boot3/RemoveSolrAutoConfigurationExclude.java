/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class RemoveSolrAutoConfigurationExclude extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `SolrAutoConfiguration` exclusion";
    }

    @Override
    public String getDescription() {
        return "Remove `SolrAutoConfiguration` exclusion from Spring Boot application annotation.";
    }

    private static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String SOLR_AUTOCONFIGURATION = "SolrAutoConfiguration";
    private static final String SOLR_AUTOCONFIGURATION_FQ = "org.springframework.boot.autoconfigure.solr." + SOLR_AUTOCONFIGURATION;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(SPRING_BOOT_APPLICATION, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation a, ExecutionContext ctx) {
                if (!TypeUtils.isOfClassType(a.getType(), SPRING_BOOT_APPLICATION)) {
                    return a;
                }

                AtomicBoolean changedAnnotation = new AtomicBoolean(false);
                List<Expression> currentArgs = a.getArguments();
                if (currentArgs == null || currentArgs.isEmpty() || currentArgs.stream().anyMatch(arg -> arg instanceof J.Empty)) {
                    return a;
                }
                List<Expression> newArgs = ListUtils.map(currentArgs, it -> {
                    if (it instanceof J.Assignment) {
                        J.Assignment as = (J.Assignment) it;
                        J.Identifier var = (J.Identifier) as.getVariable();
                        if (!var.getSimpleName().equals("exclude")) {
                            return it;
                        }
                        if (as.getAssignment() != null) {
                            if (as.getAssignment() instanceof J.NewArray) {
                                J.NewArray value = (J.NewArray) as.getAssignment();
                                if (value.getInitializer().stream().noneMatch(expr -> expr instanceof J.FieldAccess && ((J.Identifier) ((J.FieldAccess) expr).getTarget()).getSimpleName().equals(SOLR_AUTOCONFIGURATION))) {
                                    return it;
                                } else {
                                    List<Expression> values = value.getPadding().getInitializer().getElements().stream().filter(expr -> expr instanceof J.FieldAccess && !((J.Identifier) ((J.FieldAccess) expr).getTarget()).getSimpleName().equals(SOLR_AUTOCONFIGURATION)).collect(Collectors.toList());
                                    if (values.isEmpty()) {
                                        maybeRemoveImport(SOLR_AUTOCONFIGURATION_FQ);
                                        changedAnnotation.set(true);
                                        return null;
                                    } else {
                                        maybeRemoveImport(SOLR_AUTOCONFIGURATION_FQ);
                                        changedAnnotation.set(true);
                                        return as.withAssignment(((J.NewArray) as.getAssignment()).withInitializer(values));
                                    }
                                }


                            } else if (as.getAssignment() instanceof J.FieldAccess) {
                                J.FieldAccess value = (J.FieldAccess) as.getAssignment();
                                if (value.getTarget() instanceof J.Identifier && ((J.Identifier) value.getTarget()).getSimpleName().equals(SOLR_AUTOCONFIGURATION)) {
                                    maybeRemoveImport(SOLR_AUTOCONFIGURATION_FQ);
                                    changedAnnotation.set(true);
                                    return null;
                                }
                            }
                        }
                    }
                    return it;
                });
                if (changedAnnotation.get()) {
                    return a.withArguments(newArgs);
                }
                return super.visitAnnotation(a, ctx);
            }
        });
    }
}

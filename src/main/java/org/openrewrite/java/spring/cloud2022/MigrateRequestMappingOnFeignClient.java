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
package org.openrewrite.java.spring.cloud2022;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AddOrUpdateAnnotationAttribute;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class MigrateRequestMappingOnFeignClient extends Recipe {

    private static final String FEIGN_CLIENT = "org.springframework.cloud.openfeign.FeignClient";

    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";

    @Getter
    final String displayName = "Migrate `@RequestMapping` on `FeignClient` to `@FeignClient` path attribute";

    @Getter
    final String description = "Support for `@RequestMapping` over a `FeignClient` interface was removed in Spring Cloud OpenFeign 2.2.10.RELEASE.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(
                new UsesType<>(FEIGN_CLIENT, false),
                new UsesType<>(REQUEST_MAPPING, false)),
            new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    J.Annotation requestMapping = classDecl.getLeadingAnnotations().stream()
                        .filter(a -> TypeUtils.isOfClassType(a.getType(), REQUEST_MAPPING))
                        .findFirst().orElse(null);
                    J.Annotation feignClient = classDecl.getLeadingAnnotations().stream()
                        .filter(a -> TypeUtils.isOfClassType(a.getType(), FEIGN_CLIENT))
                        .findFirst().orElse(null);

                    if (requestMapping != null && feignClient != null) {
                        J.ClassDeclaration cd = classDecl;
                        if (requestMapping.getArguments() == null || requestMapping.getArguments().isEmpty()) {
                            cd = removeRequestMapping(cd, ctx);
                        } else if (requestMapping.getArguments().size() == 1) {
                            String pathValueFromRequestMapping = getPathValue(requestMapping.getArguments().get(0));
                            if (pathValueFromRequestMapping != null && !hasPathAttribute(feignClient)) {
                                cd = removeRequestMapping(cd, ctx);
                                cd = addAttributeToFeignClient(cd, ctx, pathValueFromRequestMapping);
                            }
                        }
                        return cd;
                    }
                    return super.visitClassDeclaration(classDecl, ctx);
                }

                private boolean hasPathAttribute(J.Annotation annotation) {
                    if (annotation.getArguments() == null || annotation.getArguments().isEmpty()) {
                        return false;
                    }
                    return annotation.getArguments().stream().anyMatch(arg -> {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            if (assignment.getVariable() instanceof J.Identifier) {
                                J.Identifier variable = (J.Identifier) assignment.getVariable();
                                return "path".equals(variable.getSimpleName());
                            }
                        }
                        return false;
                    });
                }

                private J.ClassDeclaration addAttributeToFeignClient(J.ClassDeclaration cd, ExecutionContext ctx, String path) {
                    return cd.withLeadingAnnotations(
                        ListUtils.map(cd.getLeadingAnnotations(), a -> (J.Annotation)
                            new AddOrUpdateAnnotationAttribute(FEIGN_CLIENT, "path",
                                path, null, true, false).getVisitor()
                                .visit(a, ctx, getCursor().getParentOrThrow())));
                }

                private J.ClassDeclaration removeRequestMapping(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    maybeRemoveImport(REQUEST_MAPPING);
                    return classDecl.withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(),
                        a -> (J.Annotation) new RemoveAnnotation(REQUEST_MAPPING).getVisitor()
                            .visit(a, ctx, getCursor().getParentOrThrow())));
                }

                private String getPathValue(Expression arg) {
                    if (arg instanceof J.Literal) {
                        J.Literal literal = (J.Literal) arg;
                        return (String) literal.getValue();
                    }
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier) {
                            J.Identifier variable = (J.Identifier) assignment.getVariable();
                            if ("path".equals(variable.getSimpleName()) || "value".equals(variable.getSimpleName())) {
                                Expression expression = assignment.getAssignment();
                                if (expression instanceof J.Literal) {
                                    J.Literal value = (J.Literal) expression;
                                    return (String) value.getValue();
                                }
                            }
                        }
                    } else if (arg instanceof J.NewArray) {
                        List<Expression> initializer = ((J.NewArray) arg).getInitializer();
                        if (initializer != null && initializer.size() == 1) {
                            return getPathValue(initializer.get(0));
                        }
                    }
                    return null;
                }
            });
    }

}

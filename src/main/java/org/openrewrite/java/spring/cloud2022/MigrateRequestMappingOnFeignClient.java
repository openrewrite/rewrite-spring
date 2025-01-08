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

import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class MigrateRequestMappingOnFeignClient extends Recipe {

    private static final String FEIGN_CLIENT = "org.springframework.cloud.openfeign.FeignClient";

    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";

    @Override
    public String getDisplayName() {
        return "Migrate `@RequestMapping` on `FeignClient` to `@FeignClient` path attribute";
    }

    @Override
    public String getDescription() {
        return "Support for `@RequestMapping` over a `FeignClient` interface was removed in Spring Cloud OpenFeign 2.2.10.RELEASE.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(
                new UsesType<>(FEIGN_CLIENT, false),
                new UsesType<>(REQUEST_MAPPING, false)),
            new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    if (!FindAnnotations.find(classDecl, "@" + FEIGN_CLIENT).isEmpty() &&
                        !FindAnnotations.find(classDecl, "@" + REQUEST_MAPPING).isEmpty()) {
                        List<J.Annotation> annotations = service(AnnotationService.class).getAllAnnotations(getCursor());
                        J.Annotation requestMapping = annotations.stream()
                            .filter(a -> TypeUtils.isOfClassType(a.getType(), REQUEST_MAPPING))
                            .findFirst().orElse(null);
                        J.Annotation feignClient = annotations.stream()
                            .filter(a -> TypeUtils.isOfClassType(a.getType(), FEIGN_CLIENT))
                            .findFirst().orElse(null);

                        if (requestMapping != null && feignClient != null) {
                            J.ClassDeclaration cd = classDecl;
                            if (requestMapping.getArguments() == null || requestMapping.getArguments().isEmpty()) {
                                cd = removeRequestMapping(classDecl);
                            } else if (requestMapping.getArguments().size() == 1) {
                                J.Assignment path = convertToPathAttribute(requestMapping.getArguments().get(0));
                                if (path != null && !hasPathAttribute(feignClient)) {
                                    cd = addAttributeToFeignClient(removeRequestMapping(classDecl), path);
                                }
                            }
                            return cd;
                        }
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

                private J.ClassDeclaration addAttributeToFeignClient(J.ClassDeclaration classDeclaration, J.Assignment path) {
                    return classDeclaration.withLeadingAnnotations(
                        ListUtils.map(classDeclaration.getLeadingAnnotations(), a -> {
                            if (TypeUtils.isOfClassType(a.getType(), FEIGN_CLIENT)) {
                                return a.withArguments(ListUtils.concat(a.getArguments(), path));
                            }
                            return a;
                        }));
                }

                private J.ClassDeclaration removeRequestMapping(J.ClassDeclaration classDecl) {
                    return classDecl.withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(), a -> {
                        if (TypeUtils.isOfClassType(a.getType(), REQUEST_MAPPING)) {
                            maybeRemoveImport(REQUEST_MAPPING);
                            return null;
                        }
                        return a;
                    }));
                }

                private J.Assignment convertToPathAttribute(Expression arg) {
                    if (arg instanceof J.Literal) {
                        J.Literal literal = (J.Literal) arg;
                        return new J.Assignment(randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                            new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                                "path", null, null),
                            new JLeftPadded<>(Space.SINGLE_SPACE, literal.withPrefix(Space.SINGLE_SPACE), Markers.EMPTY),
                            null
                        );
                    } else if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier) {
                            J.Identifier variable = (J.Identifier) assignment.getVariable();
                            if ("path".equals(variable.getSimpleName()) || "value".equals(variable.getSimpleName())) {
                                return assignment.withVariable(variable.withSimpleName("path"))
                                    .withPrefix(Space.SINGLE_SPACE);
                            }
                        }
                    }
                    return null;
                }
            });
    }

}

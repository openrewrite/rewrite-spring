/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.search;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.spring.table.ApiEndpoints;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class FindApiEndpoints extends Recipe {
    private static final List<AnnotationMatcher> REST_ENDPOINTS = Stream.of("Request", "Get", "Post", "Put", "Delete", "Patch")
            .map(method -> new AnnotationMatcher("@org.springframework.web.bind.annotation." + method + "Mapping"))
            .collect(toList());

    final transient ApiEndpoints apis = new ApiEndpoints(this);

    @Override
    public String getDisplayName() {
        return "Find API endpoints";
    }

    @Override
    public String getDescription() {
        return "Find all API endpoints that this application exposes.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                for (J.Annotation annotation : m.getAllAnnotations()) {
                    if (hasRequestMapping(annotation)) {
                        String path =
                                getCursor().getPathAsStream()
                                        .filter(J.ClassDeclaration.class::isInstance)
                                        .map(classDecl -> ((J.ClassDeclaration) classDecl).getAllAnnotations().stream()
                                                .filter(this::hasRequestMapping)
                                                .findAny()
                                                .map(classMapping -> getArg(classMapping, "value", ""))
                                                .orElse(null))
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.joining("/")) +
                                getArg(annotation, "value", "");
                        path = path.replace("//", "/");

                        JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
                        assert type != null;
                        String httpMethod = type.getClassName().startsWith("Request") ?
                                getArg(annotation, "method", "GET") :
                                type.getClassName().replace("Mapping", "").toUpperCase();

                        apis.insertRow(ctx, new ApiEndpoints.Row(
                                getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getSourcePath().toString(),
                                method.getSimpleName(),
                                httpMethod,
                                path
                        ));

                        m = SearchResult.found(m, httpMethod + " " + path);
                        break;
                    }
                }
                return m;
            }

            private boolean hasRequestMapping(J.Annotation ann) {
                for (AnnotationMatcher restEndpoint : REST_ENDPOINTS) {
                    if (restEndpoint.matches(ann)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private String getArg(J.Annotation annotation, String key, String defaultValue) {
        if (annotation.getArguments() != null) {
            for (Expression argument : annotation.getArguments()) {
                if (argument instanceof J.Literal) {
                    //noinspection ConstantConditions
                    return (String) ((J.Literal) argument).getValue();
                } else if (argument instanceof J.Assignment) {
                    J.Assignment arg = (J.Assignment) argument;
                    if (((J.Identifier) arg.getVariable()).getSimpleName().equals(key)) {
                        if (arg.getAssignment() instanceof J.FieldAccess) {
                            return ((J.FieldAccess) arg.getAssignment()).getSimpleName();
                        } else if (arg.getAssignment() instanceof J.Identifier) {
                            return ((J.Identifier) arg.getAssignment()).getSimpleName();
                        } else if (arg.getAssignment() instanceof J.Literal) {
                            //noinspection ConstantConditions
                            return (String) ((J.Literal) arg.getAssignment()).getValue();
                        }
                    }
                }
            }
        }
        return defaultValue;
    }
}

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
package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Replace method declaration @RequestMapping annotations with the associated variant
 * as defined by the request method type (GET, POST, PUT, PATCH, DELETE)
 * <p>
 * (HEAD, OPTIONS, TRACE) methods do not have associated RequestMapping variant and are not converted
 * <ul>
 * <li> @RequestMapping() changes to @GetMapping
 * <li> @RequestMapping(method = POST) changes to @PostMapping
 * <li> @RequestMapping(method = { HEAD, GET }) No change
 * </ul>
 */
public class NoRequestMappingAnnotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `@RequestMapping` annotations";
    }

    @Override
    public String getDescription() {
        return "Replace method declaration `@RequestMapping` annotations with `@GetMapping`, `@PostMapping`, etc. when possible.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-4488");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.web.bind.annotation.RequestMapping");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new NoRequestMappingAnnotationVisitor();
    }

    private static class NoRequestMappingAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher REQUEST_MAPPING_ANNOTATION_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestMapping");

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);
            if (REQUEST_MAPPING_ANNOTATION_MATCHER.matches(a) && getCursor().getParentOrThrow().getValue() instanceof J.MethodDeclaration) {

                Optional<J.Assignment> requestMethodArg = requestMethodArgument(a);
                Optional<String> requestType = requestMethodArg.isPresent() ? requestMethodArg.flatMap(this::requestMethodType) : Optional.of("GET");
                String resolvedRequestMappingAnnotationClassName = requestType.map(this::associatedRequestMapping).orElse(null);

                maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");
                maybeRemoveImport("org.springframework.web.bind.annotation.RequestMethod");
                requestType.ifPresent(requestMethod -> maybeRemoveImport("org.springframework.web.bind.annotation.RequestMethod." + requestMethod));

                // Remove the argument
                if (requestMethodArg.isPresent() && methodArgumentHasSingleType(requestMethodArg.get()) && resolvedRequestMappingAnnotationClassName != null) {
                    if (a.getArguments() != null) {
                        a = maybeAutoFormat(a, a.withArguments(ListUtils.map(a.getArguments(), arg -> requestMethodArg.get().equals(arg) ? null : arg)), ctx);
                    }
                }

                // Change the Annotation Type
                if (resolvedRequestMappingAnnotationClassName != null) {
                    maybeAddImport("org.springframework.web.bind.annotation." + resolvedRequestMappingAnnotationClassName);
                    a = (J.Annotation) new ChangeType("org.springframework.web.bind.annotation.RequestMapping",
                            "org.springframework.web.bind.annotation." + resolvedRequestMappingAnnotationClassName, false)
                            .getVisitor().visit(a, ctx, getCursor());
                }

                // if there is only one remaining argument now, and it is "path" or "value", then we can drop the key name
                if (a != null && a.getArguments() != null && a.getArguments().size() == 1) {
                    a = maybeAutoFormat(a, a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                        if (arg instanceof J.Assignment && ((J.Assignment) arg).getVariable() instanceof J.Identifier) {
                            J.Identifier ident = (J.Identifier) ((J.Assignment) arg).getVariable();
                            if ("path".equals(ident.getSimpleName()) || "value".equals(ident.getSimpleName())) {
                                return ((J.Assignment) arg).getAssignment();
                            }
                        }
                        return arg;
                    })), ctx);
                }
            }
            return a != null ? a : annotation;
        }

        private Optional<J.Assignment> requestMethodArgument(J.Annotation annotation) {
            if (annotation.getArguments() == null) {
                return Optional.empty();
            }
            return annotation.getArguments().stream()
                    .filter(arg -> arg instanceof J.Assignment
                            && ((J.Assignment) arg).getVariable() instanceof J.Identifier
                            && "method".equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName()))
                    .map(J.Assignment.class::cast)
                    .findFirst();
        }

        private boolean methodArgumentHasSingleType(J.Assignment assignment) {
            if (!(assignment.getAssignment() instanceof J.NewArray)) {
                return true;
            }
            J.NewArray newArray = (J.NewArray) assignment.getAssignment();
            return newArray.getInitializer() != null && newArray.getInitializer().size() == 1;
        }

        private Optional<String> requestMethodType(@Nullable J.Assignment assignment) {
            String method;
            if (assignment == null) {
                method = "GET";
            } else if (assignment.getAssignment() instanceof J.Identifier) {
                method = ((J.Identifier) assignment.getAssignment()).getSimpleName();
            } else if (assignment.getAssignment() instanceof J.FieldAccess) {
                method = ((J.FieldAccess) assignment.getAssignment()).getSimpleName();
            } else if (methodArgumentHasSingleType(assignment)) {
                J.NewArray newArray = (J.NewArray) assignment.getAssignment();
                assert newArray.getInitializer() != null;
                method = ((J.FieldAccess) newArray.getInitializer().get(0)).getSimpleName();
            } else {
                method = null;
            }
            return Optional.ofNullable(method);
        }

        @Nullable
        private String associatedRequestMapping(String method) {
            String methodName = null;
            switch (method) {
                case "POST":
                case "PUT":
                case "DELETE":
                case "PATCH":
                case "GET":
                    methodName = method.charAt(0) + method.toLowerCase().substring(1) + "Mapping";
                    break;
            }
            return methodName;
        }
    }
}

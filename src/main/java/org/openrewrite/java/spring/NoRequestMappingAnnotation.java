/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Replace method declaration @RequestMapping annotations with the associated variant
 * as defined by the request method type (GET, POST, PUT, PATCH, DELETE)
 * <p>
 * (HEAD, OPTIONS, TRACE) methods do not have associated RequestMapping variant and are not converted
 * <li> @RequestMapping() --> @GetMapping
 * <li> @RequestMapping(method = POST) --> @PostMapping
 * <li> @RequestMapping(method = { HEAD, GET }) --> No change
 *
 */
public class NoRequestMappingAnnotation extends Recipe {
    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new NoRequestMappingAnnotationVisitor();
    }

    private static class NoRequestMappingAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String SPRING_BIND_ANNOTATION_PACKAGE = "org.springframework.web.bind.annotation";
        private static final AnnotationMatcher REQUEST_MAPPING_ANNOTATION_MATCHER = new AnnotationMatcher(SPRING_BIND_ANNOTATION_PACKAGE + ".RequestMapping");

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);
            if (REQUEST_MAPPING_ANNOTATION_MATCHER.matches(a) && getCursor().getParentOrThrow().getValue() instanceof J.MethodDeclaration) {

                Optional<J.Assignment> requestMethodArg = requestMethodArgument(a);
                Optional<RequestMethod> requestType = requestMethodArg.isPresent() ? requestMethodArg.flatMap(this::requestMethodType) : Optional.of(RequestMethod.GET);
                String resolvedRequestMappingAnnotationClassName = requestType.map(this::associatedRequestMapping).orElse(null);

                requestType.ifPresent(requestMethod -> maybeRemoveImport(SPRING_BIND_ANNOTATION_PACKAGE + ".RequestMethod." + requestMethod));

                // Remove the argument
                if (requestMethodArg.isPresent() && methodArgumentHasSingleType(requestMethodArg.get()) && resolvedRequestMappingAnnotationClassName != null) {
                    a = maybeAutoFormat(a, a.withArguments(ListUtils.map(a.getArguments(), arg -> requestMethodArg.get().equals(arg) ? null : arg)), ctx);
                }

                // Change the Annotation Type
                if (resolvedRequestMappingAnnotationClassName != null) {
                    maybeAddImport(resolvedRequestMappingAnnotationClassName);
                    if (a.getArguments() == null || a.getArguments().isEmpty()) {
                        a = a.withTemplate(template("@"+associatedRequestMapping(requestType.get())).build(), a.getCoordinates().replace());
                    } else {
                        String annotationTemplateString = "@" + associatedRequestMapping(requestType.get()) +
                                "(" + a.getArguments().stream().map(J::print).collect(Collectors.joining(",")) + ")";
                        JavaTemplate tb = template(annotationTemplateString).build();
                        a = a.withTemplate(tb, a.getCoordinates().replace());
                    }
                }

                // if there is only one remaining argument now, and it is "path" or "value", then we can drop the key name
                if (a.getArguments() != null && a.getArguments().size() == 1) {
                    a = maybeAutoFormat(a, a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                        if (arg instanceof J.Assignment && ((J.Assignment) arg).getVariable() instanceof J.Identifier) {
                            J.Identifier ident = (J.Identifier)((J.Assignment)arg).getVariable();
                            if (ident.getSimpleName().equals("path") || ident.getSimpleName().equals("value")) {
                                return ((J.Assignment) arg).getAssignment();
                            }
                        }
                        return arg;
                    })), ctx);
                }
            }
            return a;
        }

        private Optional<J.Assignment> requestMethodArgument(J.Annotation annotation) {
            if (annotation.getArguments() == null) {
                return Optional.empty();
            }
            return annotation.getArguments().stream()
                    .filter(arg -> arg instanceof J.Assignment
                            && ((J.Assignment) arg).getVariable() instanceof J.Identifier
                            && ((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName().equals("method"))
                    .map(J.Assignment.class::cast)
                    .findFirst();
        }

        private boolean methodArgumentHasSingleType(J.Assignment assignment) {
            return !(assignment.getAssignment() instanceof J.NewArray)
                    || ((J.NewArray) assignment.getAssignment()).getInitializer().size() == 1;
        }

        private Optional<RequestMethod> requestMethodType(@Nullable J.Assignment assignment) {
            RequestMethod method;
            if (assignment == null) {
                method = RequestMethod.GET;
            }
            else if (assignment.getAssignment() instanceof J.Identifier) {
                method = RequestMethod.valueOf(((J.Identifier) assignment.getAssignment()).getSimpleName());
            }
            else if (assignment.getAssignment() instanceof J.FieldAccess) {
                method = RequestMethod.valueOf(((J.FieldAccess) assignment.getAssignment()).getSimpleName());
            }
            else if (assignment.getAssignment() instanceof J.NewArray && ((J.NewArray) assignment.getAssignment()).getInitializer().size() == 1) {
                J.NewArray newArray = ((J.NewArray) assignment.getAssignment());
                method = RequestMethod.valueOf(((J.FieldAccess)newArray.getInitializer().get(0)).getSimpleName());
            } else {
                method = null;
            }
            return Optional.ofNullable(method);
        }

        @Nullable
        private String associatedRequestMapping(RequestMethod method) {
            String methodName;
            switch (method) {
                case POST:
                case PUT:
                case DELETE:
                case PATCH:
                case GET:
                    methodName = method.name().charAt(0) + method.name().substring(1).toLowerCase() + "Mapping";
                    break;
                default:
                    methodName = null;
            }
            return methodName;
        }
    }

}

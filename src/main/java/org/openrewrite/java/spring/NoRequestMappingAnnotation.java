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
import org.openrewrite.java.internal.JavaTemplate;
import org.openrewrite.java.tree.*;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Optional;

/**
 * Method RequestMapping annotations converted
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


        public NoRequestMappingAnnotationVisitor() {
            setCursoringOn();
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);
            if (REQUEST_MAPPING_ANNOTATION_MATCHER.matches(a) && getCursor().getParentOrThrow().getValue() instanceof J.MethodDecl) {

                Optional<J.Assign> requestMethodArg = requestMethodArgument(a);
                Optional<RequestMethod> requestType = requestMethodArg.isPresent() ? requestMethodArg.flatMap(this::requestMethodType) : Optional.of(RequestMethod.GET);
                String resolvedRequestMappingAnnotationClassName = requestType.map(this::associatedRequestMapping).orElse(null);

                requestType.ifPresent(requestMethod -> maybeRemoveImport(SPRING_BIND_ANNOTATION_PACKAGE + ".RequestMethod." + requestMethod));

                // Remove the argument
                if (requestMethodArg.isPresent() && methodArgumentHasSingleType(requestMethodArg.get()) && resolvedRequestMappingAnnotationClassName != null) {
                    a = maybeAutoFormat(a, a.withArgs(ListUtils.map(a.getArgs(), arg -> requestMethodArg.get().equals(arg) ? null : arg)), ctx);
                }

                // Change the Annotation Type
                if (resolvedRequestMappingAnnotationClassName != null) {
                    maybeAddImport(resolvedRequestMappingAnnotationClassName);
                    if (a.getArgs() == null || a.getArgs().isEmpty()) {
                        JavaTemplate.Builder tb = template(associatedRequestMapping(requestType.get()));
                        a = a.withTemplate(tb.build(), a.getAnnotationType().getCoordinates().replace());
                    } else {
                        JavaTemplate.Builder tb = template(associatedRequestMapping(requestType.get()) + "(#{})");
                        a = a.withTemplate(tb.build(), a.getAnnotationType().getCoordinates().replace(), a.getArgs());
                    }
                }

                // if there is only one remaining argument now, and it is "path" or "value", then we can drop the key name
                if (a.getArgs() != null && a.getArgs().size() == 1) {
                    a = maybeAutoFormat(a, a.withArgs(ListUtils.map(a.getArgs(), arg -> {
                        if (arg instanceof J.Assign && ((J.Assign) arg).getVariable() instanceof J.Ident) {
                            J.Ident ident = (J.Ident)((J.Assign)arg).getVariable();
                            if (ident.getSimpleName().equals("path") || ident.getSimpleName().equals("value")) {
                                return ((J.Assign) arg).getAssignment();
                            }
                        }
                        return arg;
                    })), ctx);
                }
            }
            return a;
        }

        private Optional<J.Assign> requestMethodArgument(J.Annotation annotation) {
            if (annotation.getArgs() == null) {
                return Optional.empty();
            }
            return annotation.getArgs().stream()
                    .filter(arg -> arg instanceof J.Assign
                            && ((J.Assign) arg).getVariable() instanceof J.Ident
                            && ((J.Ident) ((J.Assign) arg).getVariable()).getSimpleName().equals("method"))
                    .map(J.Assign.class::cast)
                    .findFirst();
        }

        private boolean methodArgumentHasSingleType(J.Assign assign) {
            return !(assign.getAssignment() instanceof J.NewArray)
                    || ((J.NewArray) assign.getAssignment()).getInitializer().size() <= 1;
        }

        private Optional<RequestMethod> requestMethodType(@Nullable J.Assign assign) {
            RequestMethod method;
            if (assign == null) {
                method = RequestMethod.GET;
            }
            else if (assign.getAssignment() instanceof J.Ident) {
                method = RequestMethod.valueOf(((J.Ident)assign.getAssignment()).getIdent().getSimpleName());
            }
            else if (assign.getAssignment() instanceof J.FieldAccess) {
                method = RequestMethod.valueOf(((J.FieldAccess)assign.getAssignment()).getSimpleName());
            }
            else if (assign.getAssignment() instanceof J.NewArray && ((J.NewArray) assign.getAssignment()).getInitializer().size() == 1) {
                J.NewArray newArray = ((J.NewArray)assign.getAssignment());
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
                    methodName = "PostMapping";
                    break;
                case PUT:
                    methodName = "PutMapping";
                    break;
                case DELETE:
                    methodName = "DeleteMapping";
                    break;
                case PATCH:
                    methodName = "PatchMapping";
                    break;
                case GET:
                    methodName = "GetMapping";
                    break;
                default:
                    methodName = null;
            }
            return methodName;
        }
    }

}

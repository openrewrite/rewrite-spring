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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

public class ImplicitWebAnnotationNames extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove implicit web annotation names";
    }

    @Override
    public String getDescription() {
        return "Removes implicit web annotation names.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesType<>("org.springframework.web.bind.annotation.PathVariable"));
                doAfterVisit(new UsesType<>("org.springframework.web.bind.annotation.RequestParam"));
                doAfterVisit(new UsesType<>("org.springframework.web.bind.annotation.RequestHeader"));
                doAfterVisit(new UsesType<>("org.springframework.web.bind.annotation.RequestAttribute"));
                doAfterVisit(new UsesType<>("org.springframework.web.bind.annotation.CookieValue"));
                doAfterVisit(new UsesType<>("org.springframework.web.bind.annotation.ModelAttribute"));
                doAfterVisit(new UsesType<>("org.springframework.web.bind.annotation.SessionAttribute"));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ImplicitWebAnnotationNamesVisitor();
    }

    private static class ImplicitWebAnnotationNamesVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final Set<String> PARAM_ANNOTATIONS = Stream.of(
                "PathVariable",
                "RequestParam",
                "RequestHeader",
                "RequestAttribute",
                "CookieValue",
                "ModelAttribute",
                "SessionAttribute"
        ).map(className -> "org.springframework.web.bind.annotation." + className).collect(toSet());

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);

            if (PARAM_ANNOTATIONS.stream().anyMatch(annotationClass -> isOfClassType(annotation.getType(), annotationClass)) &&
                    annotation.getArguments() != null && getCursor().getParentOrThrow().getValue() instanceof J.VariableDeclarations) {

                a = maybeAutoFormat(a, a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                    Cursor varDecsCursor = getCursor().getParentOrThrow();
                    J.VariableDeclarations.NamedVariable namedVariable = varDecsCursor.<J.VariableDeclarations>getValue().getVariables().get(0);
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier && assignment.getAssignment() instanceof J.Literal) {
                            J.Identifier assignName = (J.Identifier) assignment.getVariable();
                            if ("value".equals(assignName.getSimpleName()) || "name".equals(assignName.getSimpleName())) {
                                if (maybeRemoveArg(namedVariable, (J.Literal) assignment.getAssignment())) {
                                    return null;
                                }
                            }
                        }
                    } else if (arg instanceof J.Literal) {
                        if (maybeRemoveArg(namedVariable, (J.Literal) arg)) {
                            return null;
                        }
                    }

                    return arg;
                })), ctx);
            }

            return a;
        }

        private boolean maybeRemoveArg(J.VariableDeclarations.NamedVariable namedVariable, J.Literal assignValue) {
            Object value = assignValue.getValue();
            assert value != null;
            return namedVariable.getSimpleName().equals(value);
        }
    }
}

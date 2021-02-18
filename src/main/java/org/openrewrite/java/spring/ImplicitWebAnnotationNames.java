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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.tree.J;

import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

/**
 * Remove implicit web-annotation argument names and rename the associated variable.
 * <br>
 * Note. kebab and snake case annotation argument names are excluded
 * <p>
 * <ul>
 * <li> @PathVariable(value = "p3") Long anotherName changes to @PathVariable Long p3
 * <li> @PathVariable("id") Long id changes to @PathVariable Long id
 *
 */
public class ImplicitWebAnnotationNames extends Recipe {
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
                            if (assignName.getSimpleName().equals("value") || assignName.getSimpleName().equals("name")) {
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
            if (namedVariable.getSimpleName().equals(value)) {
                return true;
            }
            // kebab and snake case argument names are not renamed
            else if (value.toString().matches("[a-z][A-Za-z0-9]*")) {
                doAfterVisit(new RenameVariable<>(namedVariable, value.toString()));
                return true;
            }
            return false;
        }
    }
}

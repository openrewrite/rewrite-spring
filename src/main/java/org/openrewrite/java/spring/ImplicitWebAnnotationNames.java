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
import org.openrewrite.marker.Marker;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

/**
 * Remove implicit web-annotation argument names and rename the associated variable.
 * <br>
 * Note. kebab and snake case annotation argument names are excluded
 * <p>
 * <li> @PathVariable(value = "p3") Long anotherName --> @PathVariable Long p3
 * <li> @PathVariable("id") Long id --> @PathVariable Long id
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

        public ImplicitWebAnnotationNamesVisitor() {
            setCursoringOn();
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);

            if (PARAM_ANNOTATIONS.stream().anyMatch(annotationClass -> isOfClassType(annotation.getType(), annotationClass)) &&
                    annotation.getArgs() != null && getCursor().getParentOrThrow().getValue() instanceof J.VariableDecls) {

                a = maybeAutoFormat(a, a.withArgs(ListUtils.map(a.getArgs(), arg -> {
                    Cursor varDecsCursor = getCursor().getParentOrThrow();
                    J.VariableDecls.NamedVar namedVar = varDecsCursor.<J.VariableDecls>getValue().getVars().get(0);
                    if (arg instanceof J.Assign) {
                        J.Assign assign = (J.Assign) arg;
                        if (assign.getVariable() instanceof J.Ident && assign.getAssignment() instanceof J.Literal) {
                            J.Ident assignName = (J.Ident) assign.getVariable();
                            if (assignName.getSimpleName().equals("value") || assignName.getSimpleName().equals("name")) {
                                if (maybeRemoveArg(namedVar, (J.Literal) assign.getAssignment())) {
                                    return null;
                                }
                            }
                        }
                    } else if (arg instanceof J.Literal) {
                        if (maybeRemoveArg(namedVar, (J.Literal) arg)) {
                            return null;
                        }
                    }

                    return arg;
                })), ctx);
            }

            return a;
        }

        private boolean maybeRemoveArg(J.VariableDecls.NamedVar namedVar, J.Literal assignValue) {
            Object value = assignValue.getValue();
            assert value != null;
            if (namedVar.getSimpleName().equals(value)) {
                return true;
            }
            // kebab and snake case argument names are not renamed
            else if (value.toString().matches("[a-z][A-Za-z0-9]*")) {
                doAfterVisit(new RenameVariable<>(namedVar, value.toString()));
                return true;
            }
            return false;
        }
    }
}

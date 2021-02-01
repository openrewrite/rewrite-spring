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
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.empty;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

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
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
            J.Annotation a = annotation;
            if (a.getArgs() == null) {
                return a;
            }

            // Removing implicit parameter --> @PathVariable(value = "p3") Long anotherName --> @PathVariable Long p3
            if (PARAM_ANNOTATIONS.stream()
                    .anyMatch(annotationClass -> isOfClassType(annotation.getType(), annotationClass)) &&
                    annotation.getArgs() != null && getCursor().getParentOrThrow().getValue() instanceof J.VariableDecls) {

                // Removing implicit parameter --> @PathVariable(value = "p3") Long anotherName --> @PathVariable Long p3
                // Collect a list of J.Assign arguments to be removed
                List<J.Assign> modifiedExpression = a.getArgs().stream()
                        .filter(z -> {
                            if (z instanceof J.Assign && ((J.Assign) z).getVariable() instanceof J.Ident) {
                                J.Ident ident = (J.Ident) ((J.Assign) z).getVariable();
                                if (ident.getSimpleName().matches("name|value")) {
                                    return true;
                                }
                            }
                            return false;
                        })
                        .map(J.Assign.class::cast)
                        .collect(Collectors.toList());

                // Remove the argument check for a rename
                if (!modifiedExpression.isEmpty()) {
                    a = a.withArgs(ListUtils.map(a.getArgs(), z -> {
                        for (J.Assign o : modifiedExpression) {
                            if (o == z) {
                                // Grab the Variable Declarations Cursor
                                Cursor varDecsCursor = getCursor().getParentOrThrow();
                                J.VariableDecls.NamedVar namedVar = varDecsCursor.<J.VariableDecls>getValue().getVars().get(0);
                                Cursor namedVarCursor = new Cursor(varDecsCursor, namedVar);
                                String newName = ((J.Literal)o.getAssignment()).getValue().toString();
                                String oldName = namedVar.getSimpleName();
                                if (newName != oldName) {
                                    RenameVariable renameVariable = new RenameVariable(namedVarCursor, newName);
                                    doAfterVisit(renameVariable);
                                }

                                // return null to remove argument
                                return null;
                            }
                        }
                        return z;
                    }));
                }
                if (a.getArgs().isEmpty()) {
                    a = a.withArgs(null);
                }
            }
            return super.visitAnnotation(a, executionContext);
        }

        @Override
        public J.VariableDecls.NamedVar visitVariable(J.VariableDecls.NamedVar variable, ExecutionContext executionContext) {
            J.VariableDecls.NamedVar namedVar = super.visitVariable(variable, executionContext);
            if(namedVar.getName().getSimpleName().equals("anotherName")){
                System.out.println("");
            };
            return namedVar;
        }
    }

    private static class RenameArgMarker implements Marker {

    }
}

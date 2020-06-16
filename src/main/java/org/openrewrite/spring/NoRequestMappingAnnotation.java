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
package org.openrewrite.spring;

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

@AutoConfigure
public class NoRequestMappingAnnotation extends JavaRefactorVisitor {
    public NoRequestMappingAnnotation() {
        setCursoringOn();
    }

    @Override
    public J visitAnnotation(J.Annotation annotation) {
        J.Annotation a = refactor(annotation, super::visitAnnotation);

        if (isOfClassType(annotation.getType(), "org.springframework.web.bind.annotation.RequestMapping") &&
                getCursor().getParentOrThrow().getTree() instanceof J.MethodDecl) {
            String toAnnotationType;
            J.Annotation.Arguments args = a.getArgs();

            if (!onlyOneMethod(args)) {
                return a;
            }

            if (args == null) {
                toAnnotationType = "GetMapping";
            } else {
                toAnnotationType = args.getArgs().stream().
                        map(arg -> arg.whenType(J.Assign.class)
                                .flatMap(assign -> assign.getVariable().whenType(J.Ident.class)
                                        .filter(key -> key.getSimpleName().equals("method"))
                                        .flatMap(key -> Optional.ofNullable(assign.getAssignment().whenType(J.Ident.class)
                                                .orElseGet(() -> assign.getAssignment().whenType(J.FieldAccess.class)
                                                        .map(J.FieldAccess::getName)
                                                        .orElse(null)))
                                                .map(methodEnum -> {
                                                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestMethod");
                                                    return methodEnum.getSimpleName().substring(0, 1) + methodEnum.getSimpleName().substring(1).toLowerCase() + "Mapping";
                                                })))
                                .orElse("GetMapping"))
                        .findAny()
                        .orElse("GetMapping");

                // drop the "method" argument
                args = args.withArgs(args.getArgs().stream()
                        .filter(arg -> arg.whenType(J.Assign.class)
                                .map(assign -> !assign.getVariable().whenType(J.Ident.class)
                                        .filter(key -> key.getSimpleName().equals("method"))
                                        .isPresent())
                                .orElse(true))
                        .collect(toList()));

                // if there is only one remaining argument now, and it is "path" or "value", then we can drop the key name
                args = args.withArgs(args.getArgs().stream()
                        .map(arg -> arg.whenType(J.Assign.class)
                                .flatMap(assign -> assign.getVariable().whenType(J.Ident.class)
                                        .filter(key -> key.getSimpleName().equals("path") || key.getSimpleName().equals("value"))
                                        .map(key -> (Expression) assign.getAssignment().withFormatting(EMPTY)))
                                .orElse(arg))
                        .collect(toList()));

                // remove the argument parentheses altogether
                if (args.getArgs().isEmpty()) {
                    args = null;
                }
            }

            if (toAnnotationType.equals("HeadMapping") || toAnnotationType.equals("OptionMapping")) {
                return a; // there is no HeadMapping or OptionMapping in Spring Web.
            }

            maybeAddImport("org.springframework.web.bind.annotation." + toAnnotationType);
            maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");

            a = a.withArgs(args)
                    .withAnnotationType(J.Ident.build(randomId(), toAnnotationType,
                            JavaType.Class.build("org.springframework.web.bind.annotation." + toAnnotationType), EMPTY));
        }

        return a;
    }

    private boolean onlyOneMethod(J.Annotation.Arguments arguments) {
        if(arguments == null) {
            return true;
        }

        for (Expression arg : arguments.getArgs()) {
            if (arg instanceof J.Assign) {
                J.Assign assignArg = (J.Assign) arg;
                if ("method".equals(assignArg.getVariable().whenType(J.Ident.class)
                        .map(J.Ident::getSimpleName).orElse("unknown"))) {
                    // if there are more than two methods specified on this annotation, it can't be replaced
                    // by a single method-specific annotation
                    return !(assignArg.getAssignment() instanceof J.NewArray) ||
                            ((J.NewArray) assignArg.getAssignment()).getInitializer().getElements().size() == 1;
                }
            }
        }

        return true;
    }
}

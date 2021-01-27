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

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.tree.J;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.Formatting.formatFirstPrefix;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

@AutoConfigure
public class ImplicitWebAnnotationNames extends JavaRefactorVisitor {
    private static final Set<String> PARAM_ANNOTATIONS = Stream.of(
            "PathVariable",
            "RequestParam",
            "RequestHeader",
            "RequestAttribute",
            "CookieValue",
            "ModelAttribute",
            "SessionAttribute"
    ).map(className -> "org.springframework.web.bind.annotation." + className).collect(toSet());

    public ImplicitWebAnnotationNames() {
        setCursoringOn();
    }

    @Override
    public J visitAnnotation(J.Annotation annotation) {
        J.Annotation a = refactor(annotation, super::visitAnnotation);

        if(PARAM_ANNOTATIONS.stream().anyMatch(annClass -> isOfClassType(annotation.getType(), annClass)) &&
                annotation.getArgs() != null && nameArgumentValue(annotation).isPresent() &&
                // ModelAttribute can be on methods as well as parameters
                getCursor().getParentOrThrow().getTree() instanceof J.VariableDecls) {
            J.Annotation.Arguments args = a.getArgs();

            if (args == null) {
                return a;
            }

            // drop the "name" argument
            args = args.withArgs(args.getArgs().stream()
                    .filter(arg -> arg.whenType(J.Assign.class)
                            .map(assign -> !assign.getVariable().whenType(J.Ident.class)
                                    .filter(key -> key.getSimpleName().equals("value") || key.getSimpleName().equals("name"))
                                    .isPresent())
                            .orElse(false))
                    .collect(toList()));

            args = args.withArgs(formatFirstPrefix(args.getArgs(), ""));

            // remove the argument parentheses altogether
            if (args.getArgs().isEmpty()) {
                args = null;
            }


            nameArgumentValue(a).ifPresent(value -> andThen(new RenameVariable(
                    getCursor().getParentOrThrow().<J.VariableDecls>getTree().getVars().get(0),
                    (String) value.getValue())));

            a = a.withArgs(args);
        }

        return a;
    }

    private Optional<J.Literal> nameArgumentValue(J.Annotation annotation) {
        J.Annotation.Arguments args = annotation.getArgs();
        return args == null ? Optional.empty() :
                args.getArgs().stream()
                        .filter(arg -> arg.whenType(J.Assign.class)
                                .filter(assign -> assign.getVariable().whenType(J.Ident.class)
                                        .map(key -> key.getSimpleName().equals("value") || key.getSimpleName().equals("name"))
                                        .orElse(false))
                                .filter(assign -> assign.getAssignment() instanceof J.Literal)
                                .map(assign -> (J.Literal) assign.getAssignment())
                                .isPresent() || arg.whenType(J.Literal.class).isPresent())
                        .findAny()
                        .flatMap(arg -> arg.whenType(J.Assign.class)
                                .map(assign -> assign.getAssignment().whenType(J.Literal.class))
                                .orElse(arg.whenType(J.Literal.class)))
                        .filter(value -> value.getValue().toString().matches("[a-z][A-Za-z0-9]*"));
    }
}

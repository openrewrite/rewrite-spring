package org.gradle.rewrite.spring;

import org.openrewrite.tree.Expression;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Type;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.tree.Formatting.EMPTY;
import static org.openrewrite.tree.J.randomId;
import static org.openrewrite.tree.TypeUtils.isOfClassType;


public class NoRequestMappingAnnotation extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "spring.web.NoRequestMappingAnnotation";
    }

    @Override
    public List<AstTransform> visitAnnotation(J.Annotation annotation) {
        return maybeTransform(annotation,
                isOfClassType(annotation.getType(), "org.springframework.web.bind.annotation.RequestMapping") &&
                getCursor().getParentOrThrow().getTree() instanceof J.MethodDecl,
                super::visitAnnotation,
                a -> {
                    String toAnnotationType;
                    J.Annotation.Arguments args = a.getArgs();

                    if (args == null) {
                        toAnnotationType = "GetMapping";
                    } else {
                        toAnnotationType = args.getArgs().stream().
                                map(arg -> arg.whenType(J.Assign.class)
                                        .flatMap(assign -> assign.getVariable().whenType(J.Ident.class)
                                                .filter(key -> key.getSimpleName().equals("method"))
                                                .flatMap(key -> assign.getAssignment().whenType(J.Ident.class)
                                                        .or(() -> assign.getAssignment().whenType(J.FieldAccess.class)
                                                                .map(J.FieldAccess::getName))
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
                                        .map(assign -> assign.getVariable().whenType(J.Ident.class)
                                                .filter(key -> key.getSimpleName().equals("method"))
                                                .isEmpty())
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

                    return a.withArgs(args)
                            .withAnnotationType(J.Ident.build(randomId(), toAnnotationType,
                                    Type.Class.build("org.springframework.web.bind.annotation." + toAnnotationType), EMPTY));
                });
    }
}

package org.openrewrite.spring;

import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

public class NoRequestMappingAnnotation extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "spring.web.NoRequestMappingAnnotation";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitAnnotation(J.Annotation annotation) {
        J.Annotation a = refactor(annotation, super::visitAnnotation);

        if(isOfClassType(annotation.getType(), "org.springframework.web.bind.annotation.RequestMapping") &&
                getCursor().getParentOrThrow().getTree() instanceof J.MethodDecl) {
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

            a = a.withArgs(args)
                    .withAnnotationType(J.Ident.build(randomId(), toAnnotationType,
                            JavaType.Class.build("org.springframework.web.bind.annotation." + toAnnotationType), EMPTY));
        }

        return a;
    }
}

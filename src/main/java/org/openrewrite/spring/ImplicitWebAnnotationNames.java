package org.openrewrite.spring;

import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.refactor.RenameVariable;
import org.openrewrite.java.tree.J;

import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

public class ImplicitWebAnnotationNames extends JavaRefactorVisitor {
    private static final Set<String> PARAM_ANNOTATIONS = Set.of(
            "PathVariable",
            "RequestParam",
            "RequestHeader",
            "RequestAttribute",
            "CookieValue",
            "ModelAttribute",
            "SessionAttribute"
    ).stream().map(className -> "org.springframework.web.bind.annotation." + className).collect(toSet());

    @Override
    public String getName() {
        return "spring.web.ImplicitWebAnnotationNames";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitAnnotation(J.Annotation annotation) {
        J.Annotation a = refactor(annotation, super::visitAnnotation);

        if(PARAM_ANNOTATIONS.stream().anyMatch(annClass -> isOfClassType(annotation.getType(), annClass)) &&
                annotation.getArgs() != null && nameArgumentValue(annotation).isPresent()) {
            J.Annotation.Arguments args = a.getArgs();

            if (args == null) {
                return a;
            }

            // drop the "method" argument
            args = args.withArgs(args.getArgs().stream()
                    .filter(arg -> arg.whenType(J.Assign.class)
                            .map(assign -> assign.getVariable().whenType(J.Ident.class)
                                    .filter(key -> key.getSimpleName().equals("value") || key.getSimpleName().equals("name"))
                                    .isEmpty())
                            .orElse(false))
                    .collect(toList()));

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
                                .map(assign -> (J.Literal) assign.getAssignment())
                                .isPresent() || arg.whenType(J.Literal.class).isPresent())
                        .findAny()
                        .flatMap(arg -> arg.whenType(J.Assign.class)
                                .map(assign -> assign.getAssignment().whenType(J.Literal.class))
                                .orElse(arg.whenType(J.Literal.class)));
    }
}

package org.gradle.rewrite.spring;

import org.openrewrite.tree.J;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import org.openrewrite.visitor.refactor.op.RenameVariable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.tree.TypeUtils.isOfClassType;

public class ImplicitWebAnnotationNames extends RefactorVisitor {
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
    public String getRuleName() {
        return "spring.web.ImplicitWebAnnotationNames";
    }

    @Override
    public List<AstTransform> visitAnnotation(J.Annotation annotation) {
        return maybeTransform(annotation,
                PARAM_ANNOTATIONS.stream().anyMatch(annClass -> isOfClassType(annotation.getType(), annClass)) &&
                        annotation.getArgs() != null && nameArgumentValue(annotation).isPresent(),
                super::visitAnnotation,
                (a, cursor) -> {
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
                            cursor.getParentOrThrow().<J.VariableDecls>getTree().getVars().get(0),
                            (String) value.getValue())));

                    return a.withArgs(args);
                });
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

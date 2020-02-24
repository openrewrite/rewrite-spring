package org.gradle.rewrite.spring;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.visitor.refactor.op.RenameVariable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.netflix.rewrite.tree.TypeUtils.isOfClassType;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

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
    public List<AstTransform> visitAnnotation(Tr.Annotation annotation) {
        return maybeTransform(annotation,
                PARAM_ANNOTATIONS.stream().anyMatch(annClass -> isOfClassType(annotation.getType(), annClass)) &&
                        annotation.getArgs() != null && nameArgumentValue(annotation).isPresent(),
                super::visitAnnotation,
                (a, cursor) -> {
                    Tr.Annotation.Arguments args = a.getArgs();

                    if (args == null) {
                        return a;
                    }

                    // drop the "method" argument
                    args = args.withArgs(args.getArgs().stream()
                            .filter(arg -> arg.whenType(Tr.Assign.class)
                                    .map(assign -> assign.getVariable().whenType(Tr.Ident.class)
                                            .filter(key -> key.getSimpleName().equals("value") || key.getSimpleName().equals("name"))
                                            .isEmpty())
                                    .orElse(false))
                            .collect(toList()));

                    // remove the argument parentheses altogether
                    if (args.getArgs().isEmpty()) {
                        args = null;
                    }

                    nameArgumentValue(a).ifPresent(value -> andThen(new RenameVariable(
                            cursor.getParentOrThrow().<Tr.VariableDecls>getTree().getVars().get(0),
                            (String) value.getValue())));

                    return a.withArgs(args);
                });
    }

    private Optional<Tr.Literal> nameArgumentValue(Tr.Annotation annotation) {
        Tr.Annotation.Arguments args = annotation.getArgs();
        return args == null ? Optional.empty() :
                args.getArgs().stream()
                        .filter(arg -> arg.whenType(Tr.Assign.class)
                                .filter(assign -> assign.getVariable().whenType(Tr.Ident.class)
                                        .map(key -> key.getSimpleName().equals("value") || key.getSimpleName().equals("name"))
                                        .orElse(false))
                                .map(assign -> (Tr.Literal) assign.getAssignment())
                                .isPresent() || arg.whenType(Tr.Literal.class).isPresent())
                        .findAny()
                        .flatMap(arg -> arg.whenType(Tr.Assign.class)
                                .map(assign -> assign.getAssignment().whenType(Tr.Literal.class))
                                .orElse(arg.whenType(Tr.Literal.class)));
    }
}

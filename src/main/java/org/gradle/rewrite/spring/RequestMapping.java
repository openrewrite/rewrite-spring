package org.gradle.rewrite.spring;

import com.netflix.rewrite.tree.Expression;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.TypeUtils;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static com.netflix.rewrite.tree.Formatting.EMPTY;
import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.stream.Collectors.toList;


public class RequestMapping extends RefactorVisitor {
    @Override
    public List<AstTransform> visitAnnotation(Tr.Annotation annotation) {
        Type.Class type = TypeUtils.asClass(annotation.getType());
        return maybeTransform(annotation,
                type != null &&
                        type.getFullyQualifiedName().equals("org.springframework.web.bind.annotation.RequestMapping"),
                super::visitAnnotation,
                a -> {
                    String toAnnotationType;
                    Tr.Annotation.Arguments args = a.getArgs();

                    if (args == null) {
                        toAnnotationType = "GetMapping";
                    } else {
                        toAnnotationType = args.getArgs().stream().
                                map(arg -> arg.whenType(Tr.Assign.class)
                                        .flatMap(assign -> assign.getVariable().whenType(Tr.Ident.class)
                                                .filter(key -> key.getSimpleName().equals("method"))
                                                .flatMap(key -> assign.getAssignment().whenType(Tr.Ident.class)
                                                        .or(() -> assign.getAssignment().whenType(Tr.FieldAccess.class)
                                                                .map(Tr.FieldAccess::getName))
                                                        .map(methodEnum -> methodEnum.getSimpleName().substring(0, 1) + methodEnum.getSimpleName().substring(1).toLowerCase() + "Mapping")))
                                        .orElse("GetMapping"))
                                .findAny()
                                .orElse("GetMapping");

                        // drop the "method" argument
                        args = args = args.withArgs(args.getArgs().stream()
                                .filter(arg -> arg.whenType(Tr.Assign.class)
                                        .map(assign -> assign.getVariable().whenType(Tr.Ident.class)
                                                .filter(key -> key.getSimpleName().equals("method"))
                                                .isEmpty())
                                        .orElse(true))
                                .collect(toList()));

                        // if there is only one remaining argument now, and it is "path" or "value", then we can drop the key name
                        args = args.withArgs(args.getArgs().stream()
                                .map(arg -> arg.whenType(Tr.Assign.class)
                                        .flatMap(assign -> assign.getVariable().whenType(Tr.Ident.class)
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

                    return a.withArgs(args)
                            .withAnnotationType(Tr.Ident.build(randomId(), toAnnotationType,
                                    Type.Class.build("org.springframework.web.bind.annotation." + toAnnotationType), EMPTY));
                });
    }
}

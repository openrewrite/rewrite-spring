package org.gradle.rewrite.spring;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static com.netflix.rewrite.tree.TypeUtils.isOfClassType;
import static java.util.stream.Collectors.toList;

public class ExplicitWebAnnotations extends RefactorVisitor {
    @Override
    public List<AstTransform> visitAnnotation(Tr.Annotation annotation) {
        return maybeTransform(annotation,
                isOfClassType(annotation.getType(), "org.springframework.web.bind.annotation.PathVariable") &&
                        annotation.getArgs() != null &&
                        annotation.getArgs().getArgs().stream().anyMatch(arg -> arg.whenType(Tr.Assign.class)
                                .flatMap(assign -> assign.getVariable().whenType(Tr.Ident.class)
                                        .map(key -> key.getSimpleName().equals("value") || key.getSimpleName().equals("name")))
                                .orElse(true)),
                super::visitAnnotation,
                a -> {
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

                    return a.withArgs(args);
                });
    }
}

package org.gradle.rewrite.spring;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static com.netflix.rewrite.tree.Formatting.firstPrefix;
import static com.netflix.rewrite.tree.Formatting.formatFirstPrefix;
import static com.netflix.rewrite.tree.TypeUtils.isOfClassType;
import static java.util.stream.Collectors.toList;

public class ConstructorInjection extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "spring.ConstructorInjection";
    }

    @Override
    public List<AstTransform> visitClassDecl(Tr.ClassDecl classDecl) {
        return maybeTransform(classDecl,
                classDecl.getFields().stream().anyMatch(this::isFieldInjected),
                super::visitClassDecl,
                Tr.ClassDecl::getBody,
                body -> {
                    List<Tree> statements = body.getStatements().stream()
                            .map(stat -> stat.whenType(Tr.VariableDecls.class)
                                    .filter(this::isFieldInjected)
                                    .map(mv -> {
                                        Tr.VariableDecls fixedField = mv
                                                .withAnnotations(mv.getAnnotations().stream()
                                                        .filter(ann -> !isFieldInjectionAnnotation(ann))
                                                        .collect(toList()))
                                                .withModifiers("private", "final");

                                        maybeRemoveImport("org.springframework.beans.factory.annotation.Autowired");

                                        return (Tree) (fixedField.getAnnotations().isEmpty() && !mv.getAnnotations().isEmpty() ?
                                                fixedField.withModifiers(formatFirstPrefix(fixedField.getModifiers(),
                                                        firstPrefix(mv.getAnnotations()))) :
                                                fixedField);
                                    })
                                    .orElse(stat))
                            .collect(toList());

                    return body.withStatements(statements);
                }
        );
    }

    private boolean isFieldInjected(Tr.VariableDecls mv) {
        return mv.getAnnotations().stream().anyMatch(this::isFieldInjectionAnnotation);
    }

    private boolean isFieldInjectionAnnotation(Tr.Annotation ann) {
        return isOfClassType(ann.getType(), "javax.inject.Inject") ||
                isOfClassType(ann.getType(), "org.springframework.beans.factory.annotation.Autowired");
    }
}

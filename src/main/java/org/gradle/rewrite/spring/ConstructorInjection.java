package org.gradle.rewrite.spring;

import org.openrewrite.tree.J;
import org.openrewrite.tree.Tree;
import org.openrewrite.tree.Type;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import org.openrewrite.visitor.refactor.op.AddAnnotation;
import org.openrewrite.visitor.refactor.op.GenerateConstructorUsingFields;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.tree.Formatting.firstPrefix;
import static org.openrewrite.tree.Formatting.formatFirstPrefix;
import static org.openrewrite.tree.J.randomId;
import static org.openrewrite.tree.TypeUtils.isOfClassType;

public class ConstructorInjection extends RefactorVisitor {
    private final boolean generateLombokRequiredArgsAnnotation;
    private final boolean generateJsr305Annotations;

    public ConstructorInjection(boolean generateLombokRequiredArgsAnnotation, boolean generateJsr305Annotations) {
        this.generateLombokRequiredArgsAnnotation = generateLombokRequiredArgsAnnotation;
        this.generateJsr305Annotations = generateJsr305Annotations;
    }

    public ConstructorInjection() {
        this(false, false);
    }

    @Override
    public String getRuleName() {
        return "spring.ConstructorInjection";
    }

    @Override
    public List<AstTransform> visitClassDecl(J.ClassDecl classDecl) {
        return maybeTransform(classDecl,
                classDecl.getFields().stream().anyMatch(this::isFieldInjected),
                super::visitClassDecl,
                (cd, cursor) -> {
                    List<Tree> statements = cd.getBody().getStatements().stream()
                            .map(stat -> stat.whenType(J.VariableDecls.class)
                                    .filter(this::isFieldInjected)
                                    .map(mv -> {
                                        J.VariableDecls fixedField = mv
                                                .withAnnotations(mv.getAnnotations().stream()
                                                        .filter(ann -> !isFieldInjectionAnnotation(ann) ||
                                                                (generateJsr305Annotations && ann.getArgs() != null && ann.getArgs().getArgs().stream()
                                                                        .anyMatch(arg -> arg.whenType(J.Assign.class)
                                                                                .map(assign -> ((J.Ident) assign.getVariable()).getSimpleName().equals("required"))
                                                                                .orElse(false))))
                                                        .map(ann -> {
                                                            if (isFieldInjectionAnnotation(ann)) {
                                                                maybeAddImport("javax.annotation.Nonnull");

                                                                Type.Class nonnullType = Type.Class.build("javax.annotation.Nonnull");
                                                                return ann
                                                                        .withAnnotationType(J.Ident.build(randomId(), "Nonnull", nonnullType,
                                                                                ann.getAnnotationType().getFormatting()))
                                                                        .withArgs(null)
                                                                        .withType(nonnullType);
                                                            }
                                                            return ann;
                                                        })
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

                    if (!hasRequiredArgsConstructor(cd)) {
                        andThen(generateLombokRequiredArgsAnnotation ?
                                new AddAnnotation(cd.getId(), "lombok.RequiredArgsConstructor") :
                                new GenerateConstructorUsingFields(cd.getId(), getInjectedFields(cd)));
                    }

                    List<String> setterNames = getInjectedFields(cd).stream()
                            .map(mv -> {
                                String name = mv.getVars().get(0).getSimpleName();
                                return "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
                            })
                            .collect(toList());

                    return cd.withBody(cd.getBody().withStatements(statements.stream()
                            .filter(stat -> stat.whenType(J.MethodDecl.class)
                                    .map(md -> !setterNames.contains(md.getSimpleName()))
                                    .orElse(true))
                            .collect(toList())));
                }
        );
    }

    private boolean hasRequiredArgsConstructor(J.ClassDecl cd) {
        Set<String> injectedFieldNames = getInjectedFields(cd).stream().map(f -> f.getVars().get(0).getSimpleName()).collect(toSet());

        return cd.getBody().getStatements().stream().anyMatch(stat -> stat.whenType(J.MethodDecl.class)
                .filter(J.MethodDecl::isConstructor)
                .map(md -> md.getParams().getParams().stream()
                        .map(p -> p.whenType(J.VariableDecls.class)
                                .map(mv -> mv.getVars().get(0).getSimpleName())
                                .orElseThrow(() -> new RuntimeException("not possible to get here")))
                        .allMatch(injectedFieldNames::contains))
                .orElse(false));
    }

    private List<J.VariableDecls> getInjectedFields(J.ClassDecl cd) {
        return cd.getBody().getStatements().stream()
                .filter(stat -> stat.whenType(J.VariableDecls.class).map(this::isFieldInjected).orElse(false))
                .map(J.VariableDecls.class::cast)
                .collect(toList());
    }

    private boolean isFieldInjected(J.VariableDecls mv) {
        return mv.getAnnotations().stream().anyMatch(this::isFieldInjectionAnnotation);
    }

    private boolean isFieldInjectionAnnotation(J.Annotation ann) {
        return isOfClassType(ann.getType(), "javax.inject.Inject") ||
                isOfClassType(ann.getType(), "org.springframework.beans.factory.annotation.Autowired");
    }
}

package org.gradle.rewrite.spring;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.visitor.refactor.op.AddAnnotation;
import com.netflix.rewrite.visitor.refactor.op.GenerateConstructorUsingFields;

import java.util.List;
import java.util.Set;

import static com.netflix.rewrite.tree.Formatting.firstPrefix;
import static com.netflix.rewrite.tree.Formatting.formatFirstPrefix;
import static com.netflix.rewrite.tree.Tr.randomId;
import static com.netflix.rewrite.tree.TypeUtils.isOfClassType;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

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
    public List<AstTransform> visitClassDecl(Tr.ClassDecl classDecl) {
        return maybeTransform(classDecl,
                classDecl.getFields().stream().anyMatch(this::isFieldInjected),
                super::visitClassDecl,
                (cd, cursor) -> {
                    List<Tree> statements = cd.getBody().getStatements().stream()
                            .map(stat -> stat.whenType(Tr.VariableDecls.class)
                                    .filter(this::isFieldInjected)
                                    .map(mv -> {
                                        Tr.VariableDecls fixedField = mv
                                                .withAnnotations(mv.getAnnotations().stream()
                                                        .filter(ann -> !isFieldInjectionAnnotation(ann) ||
                                                                (generateJsr305Annotations && ann.getArgs() != null && ann.getArgs().getArgs().stream()
                                                                        .anyMatch(arg -> arg.whenType(Tr.Assign.class)
                                                                                .map(assign -> ((Tr.Ident) assign.getVariable()).getSimpleName().equals("required"))
                                                                                .orElse(false))))
                                                        .map(ann -> {
                                                            if (isFieldInjectionAnnotation(ann)) {
                                                                maybeAddImport("javax.annotation.Nonnull");

                                                                Type.Class nonnullType = Type.Class.build("javax.annotation.Nonnull");
                                                                return ann
                                                                        .withAnnotationType(Tr.Ident.build(randomId(), "Nonnull", nonnullType,
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
                            .filter(stat -> stat.whenType(Tr.MethodDecl.class)
                                    .map(md -> !setterNames.contains(md.getSimpleName()))
                                    .orElse(true))
                            .collect(toList())));
                }
        );
    }

    private boolean hasRequiredArgsConstructor(Tr.ClassDecl cd) {
        Set<String> injectedFieldNames = getInjectedFields(cd).stream().map(f -> f.getVars().get(0).getSimpleName()).collect(toSet());

        return cd.getBody().getStatements().stream().anyMatch(stat -> stat.whenType(Tr.MethodDecl.class)
                .filter(Tr.MethodDecl::isConstructor)
                .map(md -> md.getParams().getParams().stream()
                        .map(p -> p.whenType(Tr.VariableDecls.class)
                                .map(mv -> mv.getVars().get(0).getSimpleName())
                                .orElseThrow(() -> new RuntimeException("not possible to get here")))
                        .allMatch(injectedFieldNames::contains))
                .orElse(false));
    }

    private List<Tr.VariableDecls> getInjectedFields(Tr.ClassDecl cd) {
        return cd.getBody().getStatements().stream()
                .filter(stat -> stat.whenType(Tr.VariableDecls.class).map(this::isFieldInjected).orElse(false))
                .map(Tr.VariableDecls.class::cast)
                .collect(toList());
    }

    private boolean isFieldInjected(Tr.VariableDecls mv) {
        return mv.getAnnotations().stream().anyMatch(this::isFieldInjectionAnnotation);
    }

    private boolean isFieldInjectionAnnotation(Tr.Annotation ann) {
        return isOfClassType(ann.getType(), "javax.inject.Inject") ||
                isOfClassType(ann.getType(), "org.springframework.beans.factory.annotation.Autowired");
    }
}

package org.gradle.rewrite.spring;

import com.netflix.rewrite.tree.*;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.visitor.refactor.ScopedRefactorVisitor;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.netflix.rewrite.tree.Formatting.*;
import static com.netflix.rewrite.tree.Tr.randomId;
import static com.netflix.rewrite.tree.TypeUtils.isOfClassType;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.*;

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

                    Tr.ClassDecl fixedCd = cd;

                    if (!hasRequiredArgsConstructor(cd)) {
                        if (generateLombokRequiredArgsAnnotation) {
//                            fixedCd = fixedCd.withAnnotations();
                        } else {
                            //noinspection ConstantConditions
                            String constructorArgs = getInjectedFields(cd)
                                    .map(mv -> mv.getTypeExpr().printTrimmed() + " " + mv.getVars().get(0).printTrimmed())
                                    .collect(joining(", "));

                            int lastField = 0;
                            for (int i = 0; i < statements.size(); i++) {
                                if (statements.get(i) instanceof Tr.VariableDecls) {
                                    lastField = i;
                                }
                            }

                            List<Statement> constructorParams = getInjectedFields(cd)
                                    .map(mv -> new Tr.VariableDecls(randomId(),
                                            emptyList(),
                                            emptyList(),
                                            mv.getTypeExpr() != null ? mv.getTypeExpr().withFormatting(EMPTY) : null,
                                            null,
                                            formatFirstPrefix(mv.getDimensionsBeforeName(), ""),
                                            formatFirstPrefix(mv.getVars(), " "),
                                            EMPTY))
                                    .collect(toList());

                            for (int i = 1; i < constructorParams.size(); i++) {
                                constructorParams.set(i, constructorParams.get(i).withFormatting(format(" ")));
                            }

                            Formatting constructorFormatting = formatter().format(cd.getBody());
                            Tr.MethodDecl constructor = new Tr.MethodDecl(randomId(), emptyList(),
                                    singletonList(new Tr.Modifier.Public(randomId(), EMPTY)),
                                    null,
                                    null,
                                    Tr.Ident.build(randomId(), cd.getSimpleName(), cd.getType(), format(" ")),
                                    new Tr.MethodDecl.Parameters(randomId(), constructorParams, EMPTY),
                                    null,
                                    new Tr.Block<>(randomId(), null, emptyList(), format(" "),
                                            formatter().findIndent(cd.getBody().getIndent(), cd.getBody().getStatements().toArray(Tree[]::new)).getPrefix()),
                                    null,
                                    constructorFormatting.withPrefix("\n" + constructorFormatting.getPrefix()));

                            // add assignment statements to constructor
                            andThen(new ScopedRefactorVisitor(constructor.getId()) {
                                @Override
                                public List<AstTransform> visitMethod(Tr.MethodDecl method) {
                                    return maybeTransform(method,
                                            isScope(method),
                                            super::visitMethod,
                                            Tr.MethodDecl::getBody,
                                            (body, cursor) -> body.withStatements(
                                                    TreeBuilder.buildSnippet(cursor.enclosingCompilationUnit(),
                                                            cursor,
                                                            getInjectedFields(cd).map(mv -> {
                                                                String name = mv.getVars().get(0).getSimpleName();
                                                                return "this." + name + " = " + name + ";";
                                                            }).collect(joining("\n", "", "\n"))
                                                    ))
                                    );
                                }
                            });

                            statements.add(lastField + 1, constructor);
                        }
                    }

                    return fixedCd.withBody(fixedCd.getBody().withStatements(statements));
                }
        );
    }

    private boolean hasRequiredArgsConstructor(Tr.ClassDecl cd) {
        Set<String> injectedFieldNames = getInjectedFields(cd).map(f -> f.getVars().get(0).getSimpleName()).collect(toSet());

        return cd.getBody().getStatements().stream().anyMatch(stat -> stat.whenType(Tr.MethodDecl.class)
                .filter(Tr.MethodDecl::isConstructor)
                .map(md -> md.getParams().getParams().stream()
                        .map(p -> p.whenType(Tr.VariableDecls.class)
                                .map(mv -> mv.getVars().get(0).getSimpleName())
                                .orElseThrow(() -> new RuntimeException("not possible to get here")))
                        .allMatch(injectedFieldNames::contains))
                .orElse(false));
    }

    private Stream<Tr.VariableDecls> getInjectedFields(Tr.ClassDecl cd) {
        return cd.getBody().getStatements().stream()
                .filter(stat -> stat.whenType(Tr.VariableDecls.class).map(this::isFieldInjected).orElse(false))
                .map(Tr.VariableDecls.class::cast);
    }

    private boolean isFieldInjected(Tr.VariableDecls mv) {
        return mv.getAnnotations().stream().anyMatch(this::isFieldInjectionAnnotation);
    }

    private boolean isFieldInjectionAnnotation(Tr.Annotation ann) {
        return isOfClassType(ann.getType(), "javax.inject.Inject") ||
                isOfClassType(ann.getType(), "org.springframework.beans.factory.annotation.Autowired");
    }
}

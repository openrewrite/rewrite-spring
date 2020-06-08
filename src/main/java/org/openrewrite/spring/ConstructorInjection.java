/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.spring;

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.AddAnnotation;
import org.openrewrite.java.GenerateConstructorUsingFields;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.Formatting.firstPrefix;
import static org.openrewrite.Formatting.formatFirstPrefix;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

@AutoConfigure
public class ConstructorInjection extends JavaRefactorVisitor {
    private boolean useLombokRequiredArgsAnnotation = false;
    private boolean useJsr305Annotations = false;

    public void setUseJsr305Annotations(boolean useJsr305Annotations) {
        this.useJsr305Annotations = useJsr305Annotations;
    }

    public void setUseLombokRequiredArgsAnnotation(boolean useLombokRequiredArgsAnnotation) {
        this.useLombokRequiredArgsAnnotation = useLombokRequiredArgsAnnotation;
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl cd = refactor(classDecl, super::visitClassDecl);

        if(cd.getFields().stream().anyMatch(this::isFieldInjected)) {
            List<J> statements = cd.getBody().getStatements().stream()
                    .map(stat -> stat.whenType(J.VariableDecls.class)
                            .filter(this::isFieldInjected)
                            .map(mv -> {
                                J.VariableDecls fixedField = mv
                                        .withAnnotations(mv.getAnnotations().stream()
                                                .filter(ann -> !isFieldInjectionAnnotation(ann) ||
                                                        (useJsr305Annotations && ann.getArgs() != null && ann.getArgs().getArgs().stream()
                                                                .anyMatch(arg -> arg.whenType(J.Assign.class)
                                                                        .map(assign -> ((J.Ident) assign.getVariable()).getSimpleName().equals("required"))
                                                                        .orElse(false))))
                                                .map(ann -> {
                                                    if (isFieldInjectionAnnotation(ann)) {
                                                        maybeAddImport("javax.annotation.Nonnull");

                                                        JavaType.Class nonnullType = JavaType.Class.build("javax.annotation.Nonnull");
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

                                return (J) (fixedField.getAnnotations().isEmpty() && !mv.getAnnotations().isEmpty() ?
                                        fixedField.withModifiers(formatFirstPrefix(fixedField.getModifiers(),
                                                firstPrefix(mv.getAnnotations()))) :
                                        fixedField);
                            })
                            .orElse(stat))
                    .collect(toList());

            if (!hasRequiredArgsConstructor(cd)) {
                andThen(useLombokRequiredArgsAnnotation ?
                        new AddAnnotation.Scoped(cd, "lombok.RequiredArgsConstructor") :
                        new GenerateConstructorUsingFields(cd, getInjectedFields(cd)));
            }

            List<String> setterNames = getInjectedFields(cd).stream()
                    .map(mv -> {
                        String name = mv.getVars().get(0).getSimpleName();
                        return "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
                    })
                    .collect(toList());

            cd = cd.withBody(cd.getBody().withStatements(statements.stream()
                    .filter(stat -> stat.whenType(J.MethodDecl.class)
                            .map(md -> !setterNames.contains(md.getSimpleName()))
                            .orElse(true))
                    .collect(toList())));
        }

        return cd;
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

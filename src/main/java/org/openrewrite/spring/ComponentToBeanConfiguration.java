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
import org.openrewrite.Validated;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AddAnnotation;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TreeBuilder;
import org.openrewrite.java.tree.TypeUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.formatFirstPrefix;
import static org.openrewrite.Validated.required;
import static org.openrewrite.java.JavaParser.dependenciesFromClasspath;

/**
 * Generates a new {@code @Configuration} class (or updates an existing one) with {@code @Bean} definitions for all
 * {@code @Component} annotated types and places it in the same package as the main {@code @SpringBootApplication}.
 */
// FIXME generator!
@AutoConfigure
public class ComponentToBeanConfiguration extends JavaRefactorVisitor {
    private String configurationClass;

    @Nullable
    private J.CompilationUnit existingConfiguration;

    @Nullable
    private J.CompilationUnit springBootApplication;

    private final Set<J.ClassDecl> componentsToCreateBeansDefinitionsFor = new TreeSet<>(
            Comparator.comparing(J.ClassDecl::getSimpleName));

    public ComponentToBeanConfiguration() {
        setCursoringOn();
    }

    @Override
    public Validated validate() {
        return required("configurationClass", configurationClass);
    }

    public void setConfigurationClass(String configurationClass) {
        this.configurationClass = configurationClass;
    }

//    @Override
//    public J.CompilationUnit getGenerated() {
//        if (existingConfiguration == null && springBootApplication == null) {
//            // TODO how do we warn about this, don't know where to put new configuration class?
//            return null;
//        }
//
//        return Optional.ofNullable(existingConfiguration)
//                .orElseGet(() -> {
//                    Path sourceSet = Paths.get(springBootApplication.getSourcePath())
//                            .getParent();
//
//                    String pkg = springBootApplication.getPackageDecl().getExpr().printTrimmed();
//                    for (int i = 0; i < pkg.chars().filter(n -> n == '.').count(); i++) {
//                        sourceSet = sourceSet.getParent();
//                    }
//
//                    J.CompilationUnit configurationClass = J.CompilationUnit.buildEmptyClass(sourceSet, pkg, this.configurationClass);
//                    return configurationClass.refactor()
//                            .visit(new AddAnnotation.Scoped(configurationClass.getClasses().get(0),
//                                    "org.springframework.context.annotation.Configuration"))
//                            .fix(1).getFixed();
//                }).refactor().visit(new AddBeanDefinitions()).fix().getFixed();
//    }

    private class AddBeanDefinitions extends JavaRefactorVisitor {
        public AddBeanDefinitions() {
            setCursoringOn();
        }

        @Override
        public boolean isIdempotent() {
            return false;
        }

        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl configClass = refactor(classDecl, super::visitClassDecl);
            if (!configClass.findAnnotationsOnClass("@org.springframework.context.annotation.Configuration").isEmpty()) {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                assert cu != null;

                for (J.ClassDecl component : componentsToCreateBeansDefinitionsFor) {
                    JavaType.Class componentType = TypeUtils.asClass(component.getType());
                    if (componentType == null) {
                        // TODO how to report that we can't generate a bean definition
                        continue;
                    }

                    maybeAddImport(componentType);

                    List<J.VariableDecls> fieldCollaborators = autowiredFields(component);
                    List<J.VariableDecls> constructorCollaborators = constructorInjectedFields(component);

                    List<JavaType.Class> paramTypes = emptyList();
                    String constructorArgs = "";
                    String params = "";
                    if (!fieldCollaborators.isEmpty()) {
                        params = fieldCollaborators.stream()
                                .map(param -> param.getTypeExpr().printTrimmed() + " " + param.getVars().iterator().next().printTrimmed())
                                .collect(Collectors.joining(", "));
                        paramTypes = fieldCollaborators.stream()
                                .map(J.VariableDecls::getTypeAsClass)
                                .collect(toList());
                    } else if (!constructorCollaborators.isEmpty()) {
                        params = constructorCollaborators.stream()
                                .map(param -> param.getTypeExpr().printTrimmed() + " " + param.getVars().iterator().next().printTrimmed())
                                .collect(Collectors.joining(", "));
                        paramTypes = constructorCollaborators.stream()
                                .map(J.VariableDecls::getTypeAsClass)
                                .collect(toList());
                        constructorArgs = constructorCollaborators.stream()
                                .flatMap(param -> param.getVars().stream())
                                .map(J.VariableDecls.NamedVar::getSimpleName)
                                .collect(Collectors.joining(", "));
                    }

                    String className = componentType.getClassName();
                    String lowerClassName = Character.toLowerCase(className.charAt(0)) + className.substring(1);

                    String beanDefinitionSource = "@Bean\n" +
                            className + " " + lowerClassName + "(" + params + ") {\n";

                    if (!fieldCollaborators.isEmpty()) {
                        beanDefinitionSource += className + " " + lowerClassName + " = new " + className + "();\n";
                        for (J.VariableDecls fieldCollaborator : fieldCollaborators) {
                            beanDefinitionSource += fieldCollaborator.getVars().stream().findAny()
                                    .map(field -> {
                                        String lowerName = field.getSimpleName();
                                        return lowerClassName + ".set" + Character.toUpperCase(lowerName.charAt(0)) + lowerName.substring(1) +
                                                "(" + lowerName + ");\n";
                                    })
                                    .orElse("");
                        }
                        beanDefinitionSource += "return " + lowerClassName + ";\n";
                    } else {
                        beanDefinitionSource += "return new " + className + "(" + constructorArgs + ");\n";
                    }

                    beanDefinitionSource += "}";

                    J.MethodDecl beanDefinition = TreeBuilder.buildMethodDeclaration(
                            JavaParser.fromJavaVersion()
                                    .classpath(dependenciesFromClasspath("spring-context"))
                                    .build(),
                            configClass,
                            beanDefinitionSource,
                            Stream.concat(
                                    Stream.of(
                                            JavaType.Class.build("org.springframework.context.annotation.Bean"),
                                            componentType
                                    ),
                                    paramTypes.stream()
                            ).toArray(JavaType.Class[]::new)
                    );

                    andThen(new AutoFormat(beanDefinition));

                    List<J> statements = new ArrayList<>(configClass.getBody().getStatements());
                    statements.add(beanDefinition.withPrefix("\n" + beanDefinition.getPrefix()));

                    configClass = configClass.withBody(configClass.getBody().withStatements(statements));
                }
            }

            return configClass;
        }

        private List<J.VariableDecls> autowiredFields(J.ClassDecl component) {
            return component.getFields().stream()
                    .filter(f -> f.getAnnotations().stream()
                            .anyMatch(ann ->
                                    TypeUtils.hasElementType(ann.getType(), "org.springframework.beans.factory.annotation.Autowired") ||
                                            TypeUtils.hasElementType(ann.getType(), "javax.inject.Inject")
                            )
                    )
                    .collect(toList());
        }

        private List<J.VariableDecls> constructorInjectedFields(J.ClassDecl component) {
            return component.getMethods().stream()
                    .filter(J.MethodDecl::isConstructor)
                    .max(Comparator.comparing(m -> m.getParams().getParams().size()))
                    .map(m -> m.getParams().getParams().stream()
                            .filter(J.VariableDecls.class::isInstance)
                            .map(J.VariableDecls.class::cast)
                            .collect(toList())
                    )
                    .orElse(emptyList());
        }
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

        JavaType.Class classType = TypeUtils.asClass(classDecl.getType());
        if (classType != null && classType.getFullyQualifiedName().equals(configurationClass)) {
            existingConfiguration = getCursor().firstEnclosing(J.CompilationUnit.class);
            return c;
        }

        if (!classDecl.findAnnotationsOnClass("@org.springframework.boot.autoconfigure.SpringBootApplication").isEmpty()) {
            springBootApplication = getCursor().firstEnclosing(J.CompilationUnit.class);
        }

        List<J.Annotation> components = Stream.concat(
                classDecl.findAnnotationsOnClass("@org.springframework.stereotype.Component").stream(),
                Stream.concat(
                        classDecl.findAnnotationsOnClass("@org.springframework.stereotype.Repository").stream(),
                        classDecl.findAnnotationsOnClass("@org.springframework.stereotype.Service").stream()
                )
        ).collect(toList());

        if (!components.isEmpty()) {
            List<J.Annotation> annotations = new ArrayList<>(c.getAnnotations());
            annotations.removeAll(components);
            c = c.withAnnotations(formatFirstPrefix(annotations, c.getAnnotations().get(0).getFormatting().getPrefix()));
            componentsToCreateBeansDefinitionsFor.add(c);
        }

        return c;
    }
}

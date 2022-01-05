/*
 * Copyright 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache License 2.0
 *
 * @author: fkrueger
 */
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.*;

@Incubating(since = "4.15.0")
public class AnnotateClassesDependingOnDataSource extends Recipe {

    public static final String DATABASE_INITIALIZATION_ANNOTATION = "org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization";
    private static final List<String> SPRING_BEAN_ANNOTATIONS = Arrays.asList(new String[]{
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.boot.test.context.TestComponent"
    });

    @Override
    public String getDisplayName() {
        return "Adds @DependsOnDatabaseInitialization to component or bean definition for beans depending on javax.sql.DataSource.";
    }

    @Override
    public String getDescription() {
        return "As of Spring Boot 2.5 beans depending on javax.sql.DataSource must be annotated with @DependsOnDatabaseInitialization to be initialized after DataSource. This recipe adds this annotation to Spring components depending on javax.sql.DataSource.";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                boolean dependsOnDataSource = FindClassesDependingOnDataSource.isMatch(classDecl, executionContext);
                if(dependsOnDataSource) {
                    if(isSpringComponent(cd) && isNotAnnotatedWithDatabaseInitializationAnnotation(cd)) {
                        J.ClassDeclaration annotatedSpringComponents = annotataddDataBaseInitializationAnnotation(cd);
                        return annotatedSpringComponents;
                    }
                }
                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, executionContext);

                if (isBeanDefinition(methodDeclaration)) {
                    if(shouldBeanDefinitionBeAnnotated(executionContext, methodDeclaration)) {
                        methodDeclaration = addDataBaseInitializationAnnotation(methodDeclaration);
                    }
                }
                return methodDeclaration;
            }

            private boolean shouldBeanDefinitionBeAnnotated(ExecutionContext executionContext, J.MethodDeclaration methodDeclaration) {
                String returnType = ((JavaType.FullyQualified) methodDeclaration.getReturnTypeExpression().getType()).getFullyQualifiedName();
                // is return type in list of types depending on DataSource ?
                List<String> matches = FindClassesDependingOnDataSource.getMatches(executionContext);
                boolean beanDefinitionShouldBeAnnotated = beanDeclarationHasReturnTypeDependingOnDataSource(returnType, matches) && isNotAnnotatedWith(methodDeclaration, DATABASE_INITIALIZATION_ANNOTATION);
                return beanDefinitionShouldBeAnnotated;
            }

            private boolean isBeanDefinition(J.MethodDeclaration methodDeclaration) {
                return methodDeclaration.getLeadingAnnotations().stream()
                        .map(a -> a.getAnnotationType().getType())
                        .filter(a -> JavaType.FullyQualified.class.isAssignableFrom(a.getClass()))
                        .map(JavaType.FullyQualified.class::cast)
                        .anyMatch(a -> a.getFullyQualifiedName().equals("org.springframework.context.annotation.Bean"));
            }

            private J.MethodDeclaration addDataBaseInitializationAnnotation(J.MethodDeclaration cd) {
                JavaTemplate template = createJavaTemplate(DATABASE_INITIALIZATION_ANNOTATION);
                J.MethodDeclaration j = cd.withTemplate(template, cd.getCoordinates().addAnnotation(Comparator.comparing((a) -> a.getSimpleName())));
                maybeAddImport(DATABASE_INITIALIZATION_ANNOTATION);
                return j;
            }

            private JavaTemplate createJavaTemplate(String... imports) {
                JavaTemplate template = JavaTemplate.builder(() -> getCursor(), "@DependsOnDatabaseInitialization")
                        .imports(imports)
                        .javaParser(() -> JavaParser.fromJavaVersion()
                                .classpath("spring-boot")
                                .build())
                        .build();
                return template;
            }

            private boolean beanDeclarationHasReturnTypeDependingOnDataSource(String returnType, List<String> types) {
                return types != null && types.stream().anyMatch(t -> t.equals(returnType));
            }

            private boolean isNotAnnotatedWith(J.MethodDeclaration methodDeclaration, String databaseInitializationAnnotation) {
                return methodDeclaration.getLeadingAnnotations().stream()
                        .noneMatch(a -> ((JavaType.FullyQualified)a.getAnnotationType().getType()).getFullyQualifiedName().equals(databaseInitializationAnnotation));
            }

            private J.ClassDeclaration annotataddDataBaseInitializationAnnotation(J.ClassDeclaration cd) {
                JavaTemplate template = createJavaTemplate("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization");
                J.ClassDeclaration j = cd.withTemplate(template, cd.getCoordinates().addAnnotation(Comparator.comparing((a) -> a.getSimpleName())));
                maybeAddImport("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization");
                return j;
            }

            private boolean isNotAnnotatedWithDatabaseInitializationAnnotation(J.ClassDeclaration c) {
                return c.getLeadingAnnotations().stream().noneMatch(a -> ((JavaType.FullyQualified)a.getAnnotationType().getType()).getFullyQualifiedName().equals(DATABASE_INITIALIZATION_ANNOTATION));
            }

            private boolean isSpringComponent(J.ClassDeclaration cd) {
                return cd.getLeadingAnnotations().stream()
                        .anyMatch(a -> SPRING_BEAN_ANNOTATIONS.contains(((JavaType.FullyQualified)a.getAnnotationType().getType()).getFullyQualifiedName()));
            }
        };
    }

}

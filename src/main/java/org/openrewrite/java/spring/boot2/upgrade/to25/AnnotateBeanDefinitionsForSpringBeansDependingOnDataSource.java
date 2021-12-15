/*
 * Copyright 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache License 2.0
 *
 * @author: fkrueger
 */
package org.openrewrite.java.spring.boot2.upgrade.to25;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.TypeMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.Comparator;
import java.util.Set;

@Incubating(since = "4.15.0")
public class AnnotateBeanDefinitionsForSpringBeansDependingOnDataSource extends Recipe {

    public static final String JAVAX_DATA_SOURCE = "javax.sql.DataSource";
    public static final String DATABASE_INITIALIZATION_ANNOTATION = "org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization";

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, executionContext);
                boolean isBeanDefinition = isBeanDefinition(methodDeclaration);
                if (isBeanDefinition) {
                    // get return type
                    String returnType = ((JavaType.FullyQualified) methodDeclaration.getReturnTypeExpression().getType()).getFullyQualifiedName();
                    // is return type in list of types depending on DataSource ?
                    Set<J.ClassDeclaration> types = executionContext.getMessage(FindSpringBeansDependingOnDataSource.CLASSES_USING_DATA_SOURCE);
                    if(beanDeclarationHasReturnTypeDependingOnDataSource(returnType, types)) {
                        if(isNotAnnotatedWith(methodDeclaration, DATABASE_INITIALIZATION_ANNOTATION)) {
                            J.MethodDeclaration j = annotateMethod(methodDeclaration);
                            return j;
                        }
                    }
                }
                return methodDeclaration;
            }

            private boolean isBeanDefinition(J.MethodDeclaration methodDeclaration) {
                return methodDeclaration.getLeadingAnnotations().stream()
                        .map(a -> a.getAnnotationType().getType())
                        .filter(a -> JavaType.FullyQualified.class.isAssignableFrom(a.getClass()))
                        .map(JavaType.FullyQualified.class::cast)
                        .anyMatch(a -> a.getFullyQualifiedName().equals("org.springframework.context.annotation.Bean"));
            }

            private J.MethodDeclaration annotateMethod(J.MethodDeclaration cd) {
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

            private boolean beanDeclarationHasReturnTypeDependingOnDataSource(String returnType, Set<J.ClassDeclaration> types) {
                return types != null && types.stream().anyMatch(t -> t.getType().getFullyQualifiedName().equals(returnType));
            }

            private boolean isNotAnnotatedWith(J.MethodDeclaration methodDeclaration, String databaseInitializationAnnotation) {
                return methodDeclaration.getLeadingAnnotations().stream()
                        .noneMatch(a -> ((JavaType.FullyQualified)a.getAnnotationType().getType()).getFullyQualifiedName().equals(databaseInitializationAnnotation));
            }
        };
    }

}

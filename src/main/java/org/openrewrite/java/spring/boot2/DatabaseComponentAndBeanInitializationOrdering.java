/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class DatabaseComponentAndBeanInitializationOrdering extends Recipe {

    @Override
    public String getDisplayName() {
        return "Adds @DependsOnDatabaseInitialization to Spring Beans and Components depending on javax.sql.DataSource.";
    }

    @Override
    public String getDescription() {
        return "Beans of certain well-known types, such as JdbcTemplate, will be ordered so that they are initialized after the database has been initialized. If you have a bean that works with the DataSource directly, annotate its class or @Bean method with @DependsOnDatabaseInitialization to ensure that it too is initialized after the database has been initialized.";
    }
    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        // look for spring.jpa.defer-datasource-initialization=true
        return super.visit(before, ctx);
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {

        final String javaxDataSourceFqn = "javax.sql.DataSource";
        final AnnotationMatcher dataSourceAnnotationMatcher = new AnnotationMatcher("@org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization");
        final AnnotationMatcher beanAnnotationMatcher = new AnnotationMatcher("@org.springframework.context.annotation.Bean");
        final List<AnnotationMatcher> componentAnnotationMatchers = Arrays.asList(
                new AnnotationMatcher("@org.springframework.stereotype.Repository"),
                new AnnotationMatcher("@org.springframework.stereotype.Component"),
                new AnnotationMatcher("@org.springframework.stereotype.Service"),
                new AnnotationMatcher("@org.springframework.boot.test.context.TestComponent"));

        final List<String> wellKnowDataSourceTypes = Arrays.asList("org.springframework.jdbc.core.JdbcTemplate",
            "org.jooq.DSLContext", "org.springframework.jdbc.core.JdbcOperations", "org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations");

        final List<String> wellKnownTypesConditional = Arrays.asList("org.springframework.orm.jpa.AbstractEntityManagerFactoryBean", "javax.persistence.EntityManagerFactory");

        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
                if (method.getMethodType() != null) {
                    if (isInitializationAnnoPresent(md.getLeadingAnnotations()) && isBean(md) && requiresInitializationAnnotation(method.getMethodType().getReturnType())) {
                        JavaTemplate template = JavaTemplate.builder(this::getCursor, "@DependsOnDatabaseInitialization")
                                .imports("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization")
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                        .classpath("spring-boot")
                                        .build())
                                .build();
                        md = md.withTemplate(template, md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        maybeAddImport("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization");
                    }
                }
                return md;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                if (isInitializationAnnoPresent(cd.getLeadingAnnotations()) && isComponent(cd) && requiresInitializationAnnotation(cd.getType())) {
                    JavaTemplate template = JavaTemplate.builder(this::getCursor, "@DependsOnDatabaseInitialization")
                            .imports("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization")
                            .javaParser(() -> JavaParser.fromJavaVersion()
                                    .classpath("spring-boot")
                                    .build())
                            .build();
                    cd = cd.withTemplate(template, cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    maybeAddImport("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization");
                }
                return cd;
            }

            private boolean isComponent(J.ClassDeclaration cd) {
                for (J.Annotation classAnno : cd.getLeadingAnnotations()) {
                    for (AnnotationMatcher componentMatcher : componentAnnotationMatchers) {
                        if (componentMatcher.matches(classAnno)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private boolean isBean(J.MethodDeclaration methodDeclaration) {
                for (J.Annotation leadingAnnotation : methodDeclaration.getLeadingAnnotations()) {
                    if (beanAnnotationMatcher.matches(leadingAnnotation)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean isInitializationAnnoPresent(@Nullable List<J.Annotation> annotations) {
                return annotations == null || annotations.stream().noneMatch(dataSourceAnnotationMatcher::matches);
            }

            private boolean requiresInitializationAnnotation(@Nullable JavaType type) {
                if (type == null) {
                    return false;
                }
                if (isWellKnownDataSourceInitializationType(type)) {
                    return false;
                }
                if (type instanceof JavaType.FullyQualified) {
                    JavaType.FullyQualified fq = (JavaType.FullyQualified)type;
                    // type fields
                    for (JavaType.Variable var : fq.getMembers()) {
                        if (isDataSourceType(var.getType())) {
                            return true;
                        }
                    }
                    // type methods
                    for (JavaType.Method method : fq.getMethods()) {
                        if (isDataSourceType(method.getReturnType())) {
                            return true;
                        }
                        for (JavaType parameterType : method.getParameterTypes()) {
                            if (isDataSourceType(parameterType)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            private boolean isDataSourceType(@Nullable JavaType type) {
                return type != null && TypeUtils.isAssignableTo(javaxDataSourceFqn, type);
            }

            private boolean isWellKnownDataSourceInitializationType(@Nullable JavaType type) {
                if (type != null) {
                    for (String wellKnowDataSourceType : wellKnowDataSourceTypes) {
                        if (TypeUtils.isAssignableTo(wellKnowDataSourceType, type)) {
                            return true;
                        }
                    }
                    for (String wellKnowDataSourceType : wellKnownTypesConditional) {
                        if (TypeUtils.isAssignableTo(wellKnowDataSourceType, type)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }
}
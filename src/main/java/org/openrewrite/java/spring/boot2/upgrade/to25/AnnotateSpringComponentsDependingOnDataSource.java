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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Incubating(since = "4.15.0")
public class AnnotateSpringComponentsDependingOnDataSource extends Recipe {

    private static final List<String> SPRING_BEAN_ANNOTATIONS = Arrays.asList(new String[]{
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.boot.test.context.TestComponent"
    });
    public static final String DATABASE_INITIALIZATION_ANNOTATION = "org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization";

    @Override
    public String getDisplayName() {
        return "Annotate Spring Components (e.g. @Component) depending on javax.sql.DataSource with @DependsOnDatabaseInitialization.";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);

                Optional<SearchResult> marker = cd.getMarkers().findFirst(SearchResult.class);

                if(marker.isPresent() && marker.get().getDescription().equals(FindSpringBeansDependingOnDataSource.MARKER_DESCRIPTION)) {
                    if(isSpringComponent(cd) && isNotAnnotatedWithDatabaseInitilizationAnnotation(cd)) {
                        J.ClassDeclaration annotatedSpringComponents = annotateClass(cd);
                        return annotatedSpringComponents;
                    }
                }

                return cd;
            }

            private J.ClassDeclaration annotateClass(J.ClassDeclaration cd) {
                JavaTemplate template = createJavaTemplate("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization");
                J.ClassDeclaration j = cd.withTemplate(template, cd.getCoordinates().addAnnotation(Comparator.comparing((a) -> a.getSimpleName())));
                maybeAddImport("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization");
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

            private boolean isNotAnnotatedWithDatabaseInitilizationAnnotation(J.ClassDeclaration c) {
                return c.getLeadingAnnotations().stream().noneMatch(a -> ((JavaType.FullyQualified)a.getAnnotationType().getType()).getFullyQualifiedName().equals(DATABASE_INITIALIZATION_ANNOTATION));
            }

            private boolean isSpringComponent(J.ClassDeclaration cd) {
                return cd.getLeadingAnnotations().stream()
                        .anyMatch(a -> SPRING_BEAN_ANNOTATIONS.contains(((JavaType.FullyQualified)a.getAnnotationType().getType()).getFullyQualifiedName()));
            }
        };
    }
}

/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.boot2;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class DatabaseComponentAndBeanInitializationOrderingUnconditionally extends Recipe {

    private static final String JAVAX_SQL_DATA_SOURCE = "javax.sql.DataSource";
    private static final List<String> WELL_KNOW_DATA_SOURCE_TYPES = Arrays.asList(
            "com.zaxxer.hikari.HikariDataSource",
            "javax.persistence.EntityManagerFactory",
            "liquibase.integration.spring.SpringLiquibase",
            "org.jooq.DSLContext",
            "org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.JdbcOperations",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations",
            "org.springframework.orm.jpa.AbstractEntityManagerFactoryBean"
    );

    @Getter
    final String displayName = "Unconditionally adds `@DependsOnDatabaseInitialization` to Spring Beans and Components depending on `javax.sql.DataSource`";

    @Getter
    final String description = "Beans of certain well-known types, such as `JdbcTemplate`, will be ordered so that they are initialized " +
            "after the database has been initialized. If you have a bean that works with the `DataSource` directly, " +
            "annotate its class or `@Bean` method with `@DependsOnDatabaseInitialization` to ensure that it too is " +
            "initialized after the database has been initialized. See the " +
            "[release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.5-Release-Notes#initialization-ordering) " +
            "for more. This recipe will not check if the `@DependsOnDatabaseInitialization` annotation is on the classpath. " +
            "This recipe is best combined with a precondition, as seen in `DatabaseComponentAndBeanInitializationOrdering`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        AnnotationMatcher dataSourceAnnotationMatcher = new AnnotationMatcher("@org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization");
        AnnotationMatcher beanAnnotationMatcher = new AnnotationMatcher("@org.springframework.context.annotation.Bean");
        List<AnnotationMatcher> componentAnnotationMatchers = Arrays.asList(
                new AnnotationMatcher("@org.springframework.stereotype.Repository"),
                new AnnotationMatcher("@org.springframework.stereotype.Component"),
                new AnnotationMatcher("@org.springframework.stereotype.Service"),
                new AnnotationMatcher("@org.springframework.boot.test.context.TestComponent"));

        return Preconditions.check(Preconditions.or(
                new UsesType<>("org.springframework.stereotype.Repository", false),
                new UsesType<>("org.springframework.stereotype.Repository", false),
                new UsesType<>("org.springframework.stereotype.Component", false),
                new UsesType<>("org.springframework.stereotype.Service", false),
                new UsesType<>("org.springframework.boot.test.context.TestComponent", false),
                new UsesType<>("org.springframework.context.annotation.Bean", false)
        ), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (method.getMethodType() != null) {
                    if (!isInitializationAnnoPresent(md.getLeadingAnnotations()) && isBean(md) &&
                            requiresInitializationAnnotation(method.getMethodType().getReturnType())) {
                        md = JavaTemplate.builder("@DependsOnDatabaseInitialization")
                                .imports("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization")
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "spring-boot-2.7"))
                                .build()
                                .apply(
                                        getCursor(),
                                        md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                                );
                        maybeAddImport("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization");
                    }
                }
                return md;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (!isInitializationAnnoPresent(cd.getLeadingAnnotations()) && isComponent(cd) &&
                        requiresInitializationAnnotation(cd.getType())) {
                    cd = JavaTemplate.builder("@DependsOnDatabaseInitialization")
                            .imports("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "spring-boot-2.7"))
                            .build()
                            .apply(
                                    getCursor(),
                                    cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                            );
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

            @SuppressWarnings("BooleanMethodIsAlwaysInverted")
            private boolean isInitializationAnnoPresent(@Nullable List<J.Annotation> annotations) {
                return annotations != null && annotations.stream().anyMatch(dataSourceAnnotationMatcher::matches);
            }

            private boolean requiresInitializationAnnotation(@Nullable JavaType type) {
                if (type == null) {
                    return false;
                }
                if (isWellKnownDataSourceInitializationType(type)) {
                    return false;
                }
                if (type instanceof JavaType.FullyQualified) {
                    JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
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
                return TypeUtils.isAssignableTo(JAVAX_SQL_DATA_SOURCE, type);
            }

            private boolean isWellKnownDataSourceInitializationType(@Nullable JavaType type) {
                if (type != null) {
                    for (String wellKnowDataSourceType : WELL_KNOW_DATA_SOURCE_TYPES) {
                        if (TypeUtils.isAssignableTo(wellKnowDataSourceType, type)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }
}

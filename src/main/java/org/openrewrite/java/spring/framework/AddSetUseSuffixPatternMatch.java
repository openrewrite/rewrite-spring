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
package org.openrewrite.java.spring.framework;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Recipe to preserve suffix pattern matching behavior when migrating to Spring Framework 5.3+.
 * <p>
 * Note: This recipe only applies to Spring MVC (WebMvcConfigurer). Spring WebFlux does not
 * support suffix pattern matching and therefore has no setUseSuffixPatternMatch method.
 */
public class AddSetUseSuffixPatternMatch extends Recipe {
    private static final MethodMatcher WEB_MVC_setUseSuffixPatternMatch = new MethodMatcher(
            "org.springframework.web.servlet.config.annotation.PathMatchConfigurer setUseSuffixPatternMatch(java.lang.Boolean)"
    );

    private static final String WEB_MVC_CONFIGURER = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer";
    private static final String WEB_MVC_PATH_MATCH_CONFIGURER = "org.springframework.web.servlet.config.annotation.PathMatchConfigurer";

    @Getter
    final String displayName = "Add `setUseSuffixPatternMatch(true)` in Spring MVC configuration";

    @Getter
    final String description = "In Spring Framework 5.2.4 and earlier, suffix pattern matching was enabled by default. " +
            "This meant a controller method mapped to `/users` would also match `/users.json`, `/users.xml`, etc. " +
            "Spring Framework 5.3 deprecated this behavior and changed the default to false. " +
            "This recipe adds `setUseSuffixPatternMatch(true)` to `WebMvcConfigurer` implementations " +
            "to preserve the legacy behavior during migration. " +
            "Note: This only applies to Spring MVC; Spring WebFlux does not support suffix pattern matching.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(WEB_MVC_CONFIGURER, false),
                new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                boolean isWebMvcConfigClass = false;
                if (classDecl.getImplements() != null) {
                    for (TypeTree impl : classDecl.getImplements()) {
                        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(impl.getType());
                        if (fullyQualified != null &&
                                WEB_MVC_CONFIGURER.equals(fullyQualified.getFullyQualifiedName())) {
                            isWebMvcConfigClass = true;
                            break;
                        }
                    }
                }

                if (!isWebMvcConfigClass) {
                    return classDecl;
                }

                // Check whether this class has `configurePathMatch` method
                // 1. if it already has, then check if it calls method `setUseSuffixPatternMatch`.
                //      (1) if it has `setUseSuffixPatternMatch` called, do nothing.
                //      (2) if it has not `setUseSuffixPatternMatch` called, add `setUseSuffixPatternMatch(true)` to
                //      this method
                // 2. if it has not `configurePathMatch` method, add it to this class.
                boolean configMethodExists = classDecl.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .anyMatch(AddSetUseSuffixPatternMatch::isConfigurePathMatchMethod);

                if (configMethodExists) {
                    return super.visitClassDeclaration(classDecl, ctx);
                }
                // add a `configurePathMatch` method to this class
                JavaTemplate configurePathMatchTemplate = JavaTemplate.builder(
                        "@Override public void configurePathMatch(PathMatchConfigurer configurer) { configurer" +
                                ".setUseSuffixPatternMatch(true); }")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-webmvc-5", "spring-context-5", "spring-web-5"))
                        .imports(WEB_MVC_PATH_MATCH_CONFIGURER,
                                "org.springframework.web.servlet.config.annotation.WebMvcConfigurer",
                                "org.springframework.context.annotation.Configuration")
                        .build();

                classDecl = configurePathMatchTemplate.apply(getCursor(), classDecl.getBody().getCoordinates().lastStatement());
                maybeAddImport(WEB_MVC_PATH_MATCH_CONFIGURER, false);
                return classDecl;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                              ExecutionContext ctx) {

                if (isConfigurePathMatchMethod(method)) {

                    if (FindSetUseSuffixPatternMatchMethodCall.find(method)) {
                        // do nothing - already has the call
                        return method;
                    }
                    // add the setUseSuffixPatternMatch(true) statement

                    JavaTemplate template = JavaTemplate.builder("#{any()}.setUseSuffixPatternMatch(true);")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "spring-webmvc-5", "spring-context-5", "spring-web-5"))
                            .imports(WEB_MVC_PATH_MATCH_CONFIGURER,
                                    "org.springframework.web.servlet.config.annotation.WebMvcConfigurer",
                                    "org.springframework.context.annotation.Configuration")
                            .build();

                    return template.apply(
                            getCursor(),
                            method.getBody().getCoordinates().lastStatement(),
                            ((J.VariableDeclarations) method.getParameters().get(0)).getVariables().get(0).getName()
                    );
                }

                return method;
            }
        });
    }

    private static boolean isConfigurePathMatchMethod(J.MethodDeclaration method) {
        return "configurePathMatch".equals(method.getName().getSimpleName()) &&
               method.getMethodType() != null &&
               method.getMethodType().getParameterTypes().size() == 1 &&
               WEB_MVC_PATH_MATCH_CONFIGURER.equals(method.getMethodType().getParameterTypes().get(0).toString());
    }

    private static class FindSetUseSuffixPatternMatchMethodCall extends JavaIsoVisitor<AtomicBoolean> {
        static boolean find(J j) {
            return new FindSetUseSuffixPatternMatchMethodCall()
                    .reduce(j, new AtomicBoolean()).get();
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
            if (found.get()) {
                return method;
            }
            if (WEB_MVC_setUseSuffixPatternMatch.matches(method)) {
                found.set(true);
                return method;
            }

            return super.visitMethodInvocation(method, found);
        }
    }
}

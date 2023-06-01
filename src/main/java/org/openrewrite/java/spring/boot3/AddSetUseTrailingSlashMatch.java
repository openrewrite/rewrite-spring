/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

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

public class AddSetUseTrailingSlashMatch extends Recipe {
    private static final MethodMatcher WEB_MVC_setUseTrailingSlashMatch = new MethodMatcher(
            "org.springframework.web.servlet.config.annotation.PathMatchConfigurer setUseTrailingSlashMatch(java.lang.Boolean)"
    );
    private static final MethodMatcher WEB_FLUX_setUseTrailingSlashMatch = new MethodMatcher(
            "org.springframework.web.reactive.config.PathMatchConfigurer setUseTrailingSlashMatch(java.lang.Boolean)"
    );

    private static final String WEB_MVC_CONFIGURER = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer";
    private static final String WEB_FLUX_CONFIGURER = "org.springframework.web.reactive.config.WebFluxConfigurer";

    private static final String WEB_MVC_PATH_MATCH_CONFIGURER = "org.springframework.web.servlet.config.annotation.PathMatchConfigurer";
    private static final String WEB_FLUX_PATH_MATCH_CONFIGURER = "org.springframework.web.reactive.config.PathMatchConfigurer";

    @Override
    public String getDisplayName() {
        return "Add `SetUseTrailingSlashMatch()` in configuration";
    }

    @Override
    public String getDescription() {
        return "This is part of Spring MVC and WebFlux URL Matching Changes, as of Spring Framework 6.0, the trailing" +
               " slash matching configuration option has been deprecated and its default value set to false. " +
               "This means that previously, a controller `@GetMapping(\"/some/greeting\")` would match both" +
               " `GET /some/greeting` and `GET /some/greeting/`, but it doesn't match `GET /some/greeting/` " +
               "anymore by default and will result in an HTTP 404 error. This recipe is change the default with " +
               "the global Spring MVC or Webflux configuration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesType<>(WEB_MVC_CONFIGURER, false),
                new UsesType<>(WEB_FLUX_CONFIGURER, false)
        ), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                boolean isWebConfigClass = false;
                boolean isWebMVC = false;
                if (classDecl.getImplements() != null) {
                    for (TypeTree impl : classDecl.getImplements()) {
                        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(impl.getType());
                        if (fullyQualified != null &&
                            (WEB_MVC_CONFIGURER.equals(fullyQualified.getFullyQualifiedName()) ||
                             WEB_FLUX_CONFIGURER.equals(fullyQualified.getFullyQualifiedName()))
                        ) {
                            isWebMVC = WEB_MVC_CONFIGURER.equals(fullyQualified.getFullyQualifiedName());
                            isWebConfigClass = true;
                            break;
                        }
                    }
                }

                if (!isWebConfigClass) {
                    return classDecl;
                }

                // Check whether this class has `configurePathMatch` method
                // 1. if it already has, then check if it calls method `setUseTrailingSlashMatch`.
                //      (1) if it has `setUseTrailingSlashMatch` called, do nothing.
                //      (2) if it has not `setUseTrailingSlashMatch` called, add `setUseTrailingSlashMatch(true)` to
                //      this method
                // 2. if it has not `configurePathMatch` method, add it to this class.
                boolean configMethodExists = classDecl.getBody().getStatements().stream()
                        .filter(statement -> statement instanceof J.MethodDeclaration)
                        .map(J.MethodDeclaration.class::cast)
                        .anyMatch(methodDeclaration -> isWebMVCConfigurerMatchMethod(methodDeclaration) ||
                                                       isWebFluxconfigurePathMatchingMethod(methodDeclaration));

                if (configMethodExists) {
                    return super.visitClassDeclaration(classDecl, ctx);
                } else {
                    // add a `configurePathMatch` or `configurePathMatching` method to this class
                    JavaTemplate webMvcConfigurePathMatchTemplate = JavaTemplate.builder(
                                    "@Override public void configurePathMatch(PathMatchConfigurer configurer) { configurer" +
                                    ".setUseTrailingSlashMatch(true); }")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpath("spring-webmvc", "spring-context", "spring-web"))
                            .imports(WEB_MVC_PATH_MATCH_CONFIGURER,
                                    "org.springframework.web.servlet.config.annotation.WebMvcConfigurer",
                                    "org.springframework.context.annotation.Configuration")
                            .build();

                    JavaTemplate webFluxConfigurePathMatchingTemplate =
                            JavaTemplate.builder(
                                            "@Override public void configurePathMatching(PathMatchConfigurer configurer) { configurer" +
                                            ".setUseTrailingSlashMatch(true); }")
                                    .contextSensitive()
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpath("spring-webflux", "spring-context", "spring-web"))
                                    .imports(WEB_FLUX_PATH_MATCH_CONFIGURER,
                                            "org.springframework.web.reactive.config.WebFluxConfigurer",
                                            "org.springframework.context.annotation.Configuration")
                                    .build();

                    JavaTemplate template = isWebMVC ? webMvcConfigurePathMatchTemplate : webFluxConfigurePathMatchingTemplate;
                    classDecl = template.apply(getCursor(), classDecl.getBody().getCoordinates().lastStatement());
/*                    classDecl = classDecl.withBody(
                        template.apply(
                            getCursor(),
                            classDecl.getBody().getCoordinates().lastStatement()
                        ));*/

                    String importPathMatchConfigurer = isWebMVC ? WEB_MVC_PATH_MATCH_CONFIGURER : WEB_FLUX_PATH_MATCH_CONFIGURER;
                    maybeAddImport(importPathMatchConfigurer, false);
                    return classDecl;
                }
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                              ExecutionContext ctx) {

                if (isWebMVCConfigurerMatchMethod(method) || isWebFluxconfigurePathMatchingMethod(method)) {

                    if (findSetUseTrailingSlashMatchMethodCall.find(method)) {
                        // do nothing
                        return method;
                    } else {
                        // add statement

                        JavaTemplate webMvcTemplate = JavaTemplate.builder("#{any()}.setUseTrailingSlashMatch(true);")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpath("spring-webmvc", "spring-context", "spring-web"))
                                .imports(WEB_MVC_PATH_MATCH_CONFIGURER,
                                        "org.springframework.web.servlet.config.annotation.WebMvcConfigurer",
                                        "org.springframework.context.annotation.Configuration")
                                .build();

                        JavaTemplate webFluxTemplate = JavaTemplate.builder("#{any()}.setUseTrailingSlashMatch(true);")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpath("spring-webflux", "spring-context", "spring-web"))
                                .imports(WEB_MVC_PATH_MATCH_CONFIGURER,
                                        "org.springframework.web.reactive.config.WebFluxConfigurer",
                                        "org.springframework.context.annotation.Configuration")
                                .build();
                        
                        JavaTemplate template = isWebMVCConfigurerMatchMethod(method) ? webMvcTemplate : webFluxTemplate;
                        method = template.apply(
                                getCursor(),
                                method.getBody().getCoordinates().lastStatement(),
                                ((J.VariableDeclarations) method.getParameters().get(0)).getVariables().get(0).getName()
                        );

                        return method;
                    }
                }

                return method;
            }
        });
    }

    private static boolean isWebMVCConfigurerMatchMethod(J.MethodDeclaration method) {
        return method.getName().getSimpleName().equals("configurePathMatch") &&
               method.getMethodType().getParameterTypes().size() == 1 &&
               method.getMethodType().getParameterTypes().get(0).toString().equals(WEB_MVC_PATH_MATCH_CONFIGURER);
    }

    private static boolean isWebFluxconfigurePathMatchingMethod(J.MethodDeclaration method) {
        return method.getName().getSimpleName().equals("configurePathMatching") &&
               method.getMethodType().getParameterTypes().size() == 1 &&
               method.getMethodType().getParameterTypes().get(0).toString().equals(WEB_FLUX_PATH_MATCH_CONFIGURER);
    }

    private static class findSetUseTrailingSlashMatchMethodCall extends JavaIsoVisitor<AtomicBoolean> {
        static boolean find(J j) {
            return new findSetUseTrailingSlashMatchMethodCall()
                    .reduce(j, new AtomicBoolean()).get();
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
            if (found.get()) {
                return method;
            }
            if (WEB_MVC_setUseTrailingSlashMatch.matches(method) ||
                WEB_FLUX_setUseTrailingSlashMatch.matches(method)) {
                found.set(true);
                return method;
            }

            return super.visitMethodInvocation(method, found);
        }
    }
}
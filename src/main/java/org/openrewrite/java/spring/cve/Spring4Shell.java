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
package org.openrewrite.java.spring.cve;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Set;
import java.util.function.Supplier;

public class Spring4Shell extends Recipe {
    @Override
    public String getDisplayName() {
        return "Spring4Shell fix";
    }

    @Override
    public String getDescription() {
        return "See the [blog post](https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement#status) on the issue. This recipe can be further refined as more information becomes available.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.boot.autoconfigure.SpringBootApplication");
    }

//    @Override
//    protected TreeVisitor<?, ExecutionContext> getApplicableTest() {
//        // TODO add other applicable tests around presence of dependencies below a certain version, WAR packaging, etc.
//        return new JavaVisitor<ExecutionContext>() {
//            @Override
//            public J visitJavaSourceFile(JavaSourceFile cu, ExecutionContext ctx) {
//                JavaVersion javaVersion = cu.getMarkers().findFirst(JavaVersion.class).orElse(null);
//                if (javaVersion != null && javaVersion.getMajorVersion() >= 9) {
//                    return cu.withMarkers(cu.getMarkers().searchResult());
//                }
//                return cu;
//            }
//        };
//    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final Supplier<JavaParser> javaParser = () -> JavaParser.fromJavaVersion()
                    .classpath("spring-boot-autoconfigure", "spring-beans", "spring-web", "spring-webmvc")
                    .build();

            final JavaTemplate mvcRegistration = JavaTemplate
                    .builder(this::getCursor, "" +
                            "@Bean " +
                            "public WebMvcRegistrations mvcRegistrations() {" +
                            "  return new WebMvcRegistrations() {" +
                            "    @Override" +
                            "    public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {" +
                            "      return null;" +
                            "    }" +
                            "  };" +
                            "}")
                    .javaParser(javaParser)
                    .imports(
                            "org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations",
                            "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter")
                    .build();

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, executionContext);
                Set<J.Annotation> springBootApps = FindAnnotations.find(c, "@org.springframework.boot.autoconfigure.SpringBootApplication");
                if (!springBootApps.isEmpty() && FindTypes.find(c, "org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations").isEmpty()) {
                    // add @Bean method
                    c = c.withTemplate(mvcRegistration, c.getBody().getCoordinates().addMethodDeclaration((m1, m2) -> {
                        if (m1.getSimpleName().equals("mvcRegistrations")) return 1;
                        if (m2.getSimpleName().equals("mvcRegistrations")) return -1;
                        return m1.getSimpleName().compareTo(m2.getSimpleName());
                    }));

                    maybeAddImport("org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations");
                    maybeAddImport("org.springframework.context.annotation.Bean");
                    maybeAddImport("org.springframework.web.bind.ServletRequestDataBinder");
                    maybeAddImport("org.springframework.web.context.request.NativeWebRequest");
                    maybeAddImport("org.springframework.web.method.annotation.InitBinderDataBinderFactory");
                    maybeAddImport("org.springframework.web.method.support.InvocableHandlerMethod");
                    maybeAddImport("org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter");
                    maybeAddImport("org.springframework.web.servlet.mvc.method.annotation.ServletRequestDataBinderFactory");
                }
                return c;
            }
        };
    }
}

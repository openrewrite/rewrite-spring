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
package org.openrewrite.java.spring.framework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateWebMvcConfigurerAdapter extends Recipe {
    @Override
    public String getDisplayName() {
        return "Transform classes that extend `WebMvcConfigurerAdapter` to implement the `WebMvcConfigurer` interface instead";
    }

    @Override
    public String getDescription() {
        return "As of 5.0 `WebMvcConfigurer` has default methods (made possible by a Java 8 baseline) and can be implemented directly without the need for this adapter.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter");
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>(){
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                if (cd.getExtends() != null && TypeUtils.isOfClassType(cd.getExtends().getType(), "org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter")) {
                    cd = cd.withExtends(null);
                    // This is an interesting one... WebMvcConfigurerAdapter implements WebMvcConfigurer
                    // remove the super type from the class type to prevent a stack-overflow exception when the JavaTemplate visits class type.
                    JavaType.Class type = (JavaType.Class) cd.getType();
                    if (type != null) {
                        cd = cd.withType(type.withSupertype(null));
                    }
                    cd = cd.withTemplate(JavaTemplate.builder(() -> getCursor().dropParentUntil(p -> p instanceof J.ClassDeclaration || p instanceof J.CompilationUnit), "WebMvcConfigurer")
                                    .imports("org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .classpath("spring-webmvc")
                                            .build())
                                    .build(),
                            cd.getCoordinates().addImplementsClause());
                    cd = (J.ClassDeclaration) new RemoveSuperStatementVisitor().visitNonNull(cd, executionContext, getCursor());
                    maybeRemoveImport("org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter");
                    maybeAddImport("org.springframework.web.servlet.config.annotation.WebMvcConfigurer");
                }
                return cd;
            }

            class RemoveSuperStatementVisitor extends JavaIsoVisitor<ExecutionContext> {
                final MethodMatcher wm = new MethodMatcher("org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter *(..)");
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                    J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                    if (wm.matches(method.getMethodType())) {
                        return null;
                    }
                    return mi;
                }
            }
        };
    }
}

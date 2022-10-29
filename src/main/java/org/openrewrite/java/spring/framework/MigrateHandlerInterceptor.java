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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class MigrateHandlerInterceptor extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate `HandlerInterceptorAdapter` to `HandlerInterceptor`";
    }

    @Override
    public String getDescription() {
        return "Deprecated as of 5.3 in favor of implementing `HandlerInterceptor` and/or `AsyncHandlerInterceptor`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.web.servlet.handler.HandlerInterceptorAdapter");
    }

    // It will therefore be necessary to use the interfaces of these classes, respectively org.springframework.web.servlet.HandlerInterceptor and org.springframework.web.servlet.config.annotation.WebMvcConfigurer.
    //In order to replace these Adapter classes with interfaces you would have to:
    //- Edit import
    //- Replace extends <className> by implements <interfaceName>
    //- Replace super.<MethodName> calls by <InterfaceName>.super.<MethodName>

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (classDecl.getExtends() != null && TypeUtils.isOfClassType(classDecl.getExtends().getType(),
                        "org.springframework.web.servlet.handler.HandlerInterceptorAdapter")) {
                    cd = cd.withExtends(null);
                    cd = cd.withImplements(singletonList(TypeTree.build("HandlerInterceptor")
                            .withType(JavaType.buildType("org.springframework.web.servlet.HandlerInterceptor")))
                    );

                    // temporary
                    cd = cd.getPadding().withImplements(JContainer.withElements(requireNonNull(cd.getPadding().getImplements()),
                            ListUtils.mapFirst(cd.getPadding().getImplements().getElements(),
                                    anImplements -> anImplements.withPrefix(Space.format(" ")))));

                    maybeAddImport("org.springframework.web.servlet.HandlerInterceptor");
                    doAfterVisit(new RemoveImport<>("org.springframework.web.servlet.handler.HandlerInterceptorAdapter", true));
                    return autoFormat(cd, requireNonNull(cd.getImplements()).get(0), ctx, getCursor().getParentOrThrow());
                }
                return cd;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getSelect() instanceof J.Identifier) {
                    if (((J.Identifier) method.getSelect()).getSimpleName().equals("super")) {
                        return method.withSelect(TypeTree.build("HandlerInterceptor.super")
                                .withType(JavaType.buildType("org.springframework.web.servlet.HandlerInterceptor")));
                    }
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }
}

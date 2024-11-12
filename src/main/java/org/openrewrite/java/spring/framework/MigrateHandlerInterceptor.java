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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class MigrateHandlerInterceptor extends Recipe {

    private static final String HANDLER_INTERCEPTOR_ADAPTER = "org.springframework.web.servlet.handler.HandlerInterceptorAdapter";
    private static final String HANDLER_INTERCEPTOR = "org.springframework.web.servlet.HandlerInterceptor";

    @Override
    public String getDisplayName() {
        return "Migrate `HandlerInterceptorAdapter` to `HandlerInterceptor`";
    }

    @Override
    public String getDescription() {
        return "Deprecated as of 5.3 in favor of implementing `HandlerInterceptor` and/or `AsyncHandlerInterceptor`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(HANDLER_INTERCEPTOR_ADAPTER, false), new JavaVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
                if (cd.getExtends() == null || !TypeUtils.isOfClassType(cd.getExtends().getType(), HANDLER_INTERCEPTOR_ADAPTER)) {
                    return cd;
                }

                maybeAddImport(HANDLER_INTERCEPTOR);
                maybeRemoveImport(HANDLER_INTERCEPTOR_ADAPTER);

                cd = cd.withExtends(null)
                        .withImplements(singletonList(TypeTree.build("HandlerInterceptor")
                                .withType(JavaType.buildType(HANDLER_INTERCEPTOR))));

                return autoFormat(cd, requireNonNull(cd.getImplements()).get(0), ctx, getCursor().getParentOrThrow());
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (mi.getMethodType() != null &&
                        TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), HANDLER_INTERCEPTOR) &&
                        mi.getSelect() instanceof J.Identifier) {
                    if ("super".equals(((J.Identifier) mi.getSelect()).getSimpleName())) {
                        if ("preHandle".equals(mi.getSimpleName())) {
                            return JavaTemplate.builder("true").build().apply(getCursor(), mi.getCoordinates().replace());
                        } else if ("postHandle".equals(mi.getSimpleName()) || "afterCompletion".equals(mi.getSimpleName())) {
                            return null;
                        }
                    }
                }
                return mi;
            }
        });
    }
}

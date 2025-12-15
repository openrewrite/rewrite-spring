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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Collections.singletonList;

public class MigrateHandlerInterceptor extends Recipe {

    private static final String HANDLER_INTERCEPTOR_ADAPTER = "org.springframework.web.servlet.handler.HandlerInterceptorAdapter";
    private static final String HANDLER_INTERCEPTOR_INTERFACE = "org.springframework.web.servlet.HandlerInterceptor";

    private static final MethodMatcher PRE_HANDLE = new MethodMatcher("org.springframework.web.servlet.HandlerInterceptor preHandle(..)");
    private static final MethodMatcher POST_HANDLE = new MethodMatcher("org.springframework.web.servlet.HandlerInterceptor postHandle(..)");
    private static final MethodMatcher AFTER_COMPLETION = new MethodMatcher("org.springframework.web.servlet.HandlerInterceptor afterCompletion(..)");

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

                maybeRemoveImport(HANDLER_INTERCEPTOR_ADAPTER);
                maybeAddImport(HANDLER_INTERCEPTOR_INTERFACE);

                TypeTree implments = TypeTree.build("HandlerInterceptor")
                        .withType(JavaType.buildType(HANDLER_INTERCEPTOR_INTERFACE));
                cd = cd.withExtends(null).withImplements(singletonList(implments));
                return autoFormat(cd, implments, ctx, getCursor().getParentOrThrow());
            }

            @Override
            public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (mi.getMethodType() != null &&
                    TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), HANDLER_INTERCEPTOR_INTERFACE) &&
                    mi.getSelect() instanceof J.Identifier && "super".equals(((J.Identifier) mi.getSelect()).getSimpleName())) {
                    if (PRE_HANDLE.matches(mi)) {
                        // No need to call super for the hardcoded `true` return value there
                        return JavaTemplate.apply("true", getCursor(), mi.getCoordinates().replace());
                    }
                    if (POST_HANDLE.matches(mi) || AFTER_COMPLETION.matches(mi)) {
                        return null; // No need to call super for empty methods there
                    }
                }
                return mi;
            }
        });
    }
}

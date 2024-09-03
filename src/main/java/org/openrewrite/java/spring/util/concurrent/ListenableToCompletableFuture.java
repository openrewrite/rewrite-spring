/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.util.concurrent;

import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class ListenableToCompletableFuture extends JavaVisitor<ExecutionContext> {

    private static final MethodMatcher COMPLETABLE_METHOD_MATCHER = new MethodMatcher("org.springframework.util.concurrent.ListenableFuture completable()");

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
        TreeVisitor<?, ExecutionContext> visitor = new ChangeType(
                "org.springframework.util.concurrent.ListenableFuture",
                "java.util.concurrent.CompletableFuture",
                null).getVisitor();
        J.CompilationUnit cu = (J.CompilationUnit) super.visitCompilationUnit(compilationUnit, ctx);
        cu = (J.CompilationUnit) visitor.visit(cu, ctx, getCursor().getParent());
        return cu;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        return (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

        if (COMPLETABLE_METHOD_MATCHER.matches(mi)) {
            return mi.getSelect().withPrefix(mi.getPrefix());
        }

        return mi;
    }
}

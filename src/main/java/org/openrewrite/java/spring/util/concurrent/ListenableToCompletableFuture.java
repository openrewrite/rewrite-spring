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
package org.openrewrite.java.spring.util.concurrent;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicBoolean;

public class ListenableToCompletableFuture extends Recipe {

    private static final String LISTENABLE_FUTURE = "org.springframework.util.concurrent.ListenableFuture";

    private static final String LISTENABLE_FUTURE_CALLBACK = "org.springframework.util.concurrent.ListenableFutureCallback";

    private static final MethodMatcher ADD_CALLBACK_MATCHER = new MethodMatcher(LISTENABLE_FUTURE + " addCallback(..)", true);

    @Getter
    final String displayName = "Migrate `ListenableFuture` to `CompletableFuture`";

    @Getter
    final String description = "Spring Framework 6.0 removed `org.springframework.util.concurrent.ListenableFuture` in favor of " +
            "`java.util.concurrent.CompletableFuture`. This recipe migrates `ListenableFuture` types, along with " +
            "their `addCallback` invocations and `ListenableFutureCallback` implementations, to `CompletableFuture`. " +
            "Files with an `addCallback` invocation that can't be converted are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesType<>(LISTENABLE_FUTURE, false),
                new UsesType<>(LISTENABLE_FUTURE_CALLBACK, false)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
                cu = (J.CompilationUnit) new ListenableFutureCallbackToBiConsumerVisitor().visitNonNull(cu, ctx);
                cu = (J.CompilationUnit) new SuccessFailureCallbackToBiConsumerVisitor().visitNonNull(cu, ctx);
                if (hasUnconvertedSuccessFailureCallback(cu)) {
                    return compilationUnit;
                }
                cu = (J.CompilationUnit) new ChangeMethodName(
                        LISTENABLE_FUTURE + " addCallback(..)", "whenComplete", true, true)
                        .getVisitor().visit(cu, ctx, getCursor().getParent());
                return (J.CompilationUnit) new ChangeType(
                        LISTENABLE_FUTURE,
                        "java.util.concurrent.CompletableFuture", null)
                        .getVisitor().visit(cu, ctx, getCursor().getParent());
            }
        });
    }

    private static boolean hasUnconvertedSuccessFailureCallback(J.CompilationUnit cu) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                if (ADD_CALLBACK_MATCHER.matches(method) && method.getArguments().size() == 2) {
                    found.set(true);
                }
                return super.visitMethodInvocation(method, found);
            }
        }.reduce(cu, new AtomicBoolean()).get();
    }
}

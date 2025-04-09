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

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

public class ListenableToCompletableFuture extends JavaIsoVisitor<ExecutionContext> {
    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
        J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
        cu = (J.CompilationUnit) new ListenableFutureCallbackToBiConsumerVisitor().visitNonNull(cu, ctx);
        cu = (J.CompilationUnit) new SuccessFailureCallbackToBiConsumerVisitor().visitNonNull(cu, ctx);
        cu = (J.CompilationUnit) new ChangeMethodName(
                "org.springframework.util.concurrent.ListenableFuture addCallback(..)", "whenComplete", true, true)
                .getVisitor().visit(cu, ctx, getCursor().getParent());
        cu = (J.CompilationUnit) new ChangeType(
                "org.springframework.util.concurrent.ListenableFuture",
                "java.util.concurrent.CompletableFuture", null, null)
                .getVisitor().visit(cu, ctx, getCursor().getParent());
        return cu;
    }
}

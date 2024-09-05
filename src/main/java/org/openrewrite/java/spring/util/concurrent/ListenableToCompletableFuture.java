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
import org.openrewrite.java.*;
import org.openrewrite.java.tree.J;
import org.openrewrite.staticanalysis.RemoveUnneededBlock;

public class ListenableToCompletableFuture extends JavaVisitor<ExecutionContext> {

    // XXX TODO Reconsider matching original ListenableFuture instead of CompletableFuture
    // These matcher patterns assume that ChangeType has already ran first; hence the use of CompletableFuture
    private static final MethodMatcher COMPLETABLE_MATCHER =
            new MethodMatcher("java.util.concurrent.CompletableFuture completable()");
    private static final MethodMatcher ADD_CALLBACK_SUCCESS_FAILURE_MATCHER = new MethodMatcher(
            "java.util.concurrent.CompletableFuture whenComplete(org.springframework.util.concurrent.SuccessCallback, org.springframework.util.concurrent.FailureCallback)");
    private static final MethodMatcher ADD_CALLBACK_LISTENABLE_FUTURE_CALLBACK_MATCHER = new MethodMatcher(
            "java.util.concurrent.CompletableFuture whenComplete(org.springframework.util.concurrent.ListenableFutureCallback)");


    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
        J.CompilationUnit cu = compilationUnit;

        // Delegate to other visitors to handle the bulk of the work
        cu = (J.CompilationUnit) new ListenableFutureCallbackToBiConsumerVisitor().visit(cu, ctx);
        cu = (J.CompilationUnit) new ChangeMethodName(
                "org.springframework.util.concurrent.ListenableFuture addCallback(..)", "whenComplete", true, true)
                .getVisitor().visit(cu, ctx);
        cu = (J.CompilationUnit) new ChangeType(
                "org.springframework.util.concurrent.ListenableFuture", "java.util.concurrent.CompletableFuture", null)
                .getVisitor().visit(cu, ctx, getCursor().getParent());

        // Only now replace method invocations below
        cu = (J.CompilationUnit) super.visitCompilationUnit(cu, ctx);
        return cu;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

        if (COMPLETABLE_MATCHER.matches(mi)) {
            return mi.getSelect().withPrefix(mi.getPrefix());
        }
        if (ADD_CALLBACK_LISTENABLE_FUTURE_CALLBACK_MATCHER.matches(mi)) {
            return mi; // XXX Change method type still?
        }
        if (ADD_CALLBACK_SUCCESS_FAILURE_MATCHER.matches(mi)) {
            return replaceSuccessFailureCallback(mi, ctx);
        }
        return mi;
    }

    private J.MethodInvocation replaceSuccessFailureCallback(J.MethodInvocation mi, ExecutionContext ctx) {
        mi = (J.MethodInvocation) new MemberReferenceToMethodInvocation().visitNonNull(mi, ctx, getCursor().getParent());

        J.Lambda successCallback = (J.Lambda) mi.getArguments().get(0);
        J.Lambda failureCallback = (J.Lambda) mi.getArguments().get(1);

        J.Identifier successParam = ((J.VariableDeclarations) successCallback.getParameters().getParameters().get(0)).getVariables().get(0).getName();
        J.Identifier failureParam = ((J.VariableDeclarations) failureCallback.getParameters().getParameters().get(0)).getVariables().get(0).getName();
        J.MethodInvocation whenComplete = JavaTemplate.builder(String.format(
                        "(%s, %s) -> {\n" +
                        "    if (#{any()} == null) { #{any()}; }" +
                        "    else { #{any()}; }\n" +
                        "}",
                        successParam,
                        failureParam
                ))
                .contextSensitive()
                .build()
                .apply(getCursor(), mi.getCoordinates().replaceArguments(),
                        failureParam,
                        successCallback.getBody(),
                        failureCallback.getBody());

        return (J.MethodInvocation) new RemoveUnneededBlock().getVisitor().visitNonNull(whenComplete, ctx, getCursor().getParent());
    }
}

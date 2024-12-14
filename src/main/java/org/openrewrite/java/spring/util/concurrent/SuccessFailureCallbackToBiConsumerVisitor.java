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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.spring.util.MemberReferenceToMethodInvocation;
import org.openrewrite.java.tree.J;
import org.openrewrite.staticanalysis.RemoveUnneededBlock;

class SuccessFailureCallbackToBiConsumerVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final MethodMatcher ADD_CALLBACK_SUCCESS_FAILURE_MATCHER = new MethodMatcher(
            "org.springframework.util.concurrent.ListenableFuture addCallback(" +
            "org.springframework.util.concurrent.SuccessCallback, " +
            "org.springframework.util.concurrent.FailureCallback)");

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
        if (ADD_CALLBACK_SUCCESS_FAILURE_MATCHER.matches(mi)) {
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
        return mi;
    }
}

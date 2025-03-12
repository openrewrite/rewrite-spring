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

import lombok.AllArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.spring.util.MemberReferenceToMethodInvocation;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.staticanalysis.RemoveUnneededBlock;

import java.util.Objects;

class SuccessFailureCallbackToBiConsumerVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final MethodMatcher ADD_CALLBACK_SUCCESS_FAILURE_MATCHER = new MethodMatcher(
            "org.springframework.util.concurrent.ListenableFuture addCallback(" +
                    "org.springframework.util.concurrent.SuccessCallback, " +
                    "org.springframework.util.concurrent.FailureCallback)");
    private static final String FQN_KAFKA_FAILURE_CALLBACK = "org.springframework.kafka.core.KafkaFailureCallback";
    private static final MethodMatcher GET_FAILED_PRODUCER_RECORD = new MethodMatcher("org.springframework.kafka.core.KafkaProducerException getFailedProducerRecord()");
    private static final String FQN_KAFKA_PRODUCER_EXCEPTION = "org.springframework.kafka.core.KafkaProducerException";

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
        if (ADD_CALLBACK_SUCCESS_FAILURE_MATCHER.matches(mi)) {
            mi = (J.MethodInvocation) new MemberReferenceToMethodInvocation().visitNonNull(mi, ctx, getCursor().getParent());

            /* If the first argument is a method invocation, very probably it's a mockito.when(), we should not do anything here
             * For example:
             *   ListenableFuture<SendResult> future = mock(ListenableFuture.class);
             *   doAnswer(i -> {
             *     return null;
             *   }).when(future).whenComplete(any(), any());
             */
            if (mi.getArguments().get(0) instanceof J.MethodInvocation) {
                return mi;
            }

            J.Lambda successCallback = (J.Lambda) mi.getArguments().get(0);

            boolean isKafkaFailureCallback = false;
            J.Lambda failureCallback;
            if (mi.getArguments().get(1) instanceof J.TypeCast) {
                // In this case, assume it's casted to `org.springframework.kafka.core.KafkaFailureCallback` only
                failureCallback = (J.Lambda) ((J.TypeCast) mi.getArguments().get(1)).getExpression();
                isKafkaFailureCallback = true;
            } else {
                failureCallback = (J.Lambda) mi.getArguments().get(1);
            }

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

            whenComplete = (J.MethodInvocation) new RemoveUnneededBlock().getVisitor().visitNonNull(whenComplete, ctx, getCursor().getParent());
            if (isKafkaFailureCallback) {
                J.Lambda biConsumer = (J.Lambda) whenComplete.getArguments().get(0);
                J.VariableDeclarations secondArg = (J.VariableDeclarations) biConsumer.getParameters().getParameters().get(1);
                J.Identifier secondArgName = secondArg.getVariables().get(0).getName();
                doAfterVisit(new MigrateKafkaProducerExceptionVisitor(secondArgName));
                maybeRemoveImport(FQN_KAFKA_FAILURE_CALLBACK);
            }
            return whenComplete;
        }
        return mi;
    }

    @AllArgsConstructor
    public static class MigrateKafkaProducerExceptionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private Expression name;

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (GET_FAILED_PRODUCER_RECORD.matches(method) && Objects.equals(method.getSelect().printTrimmed(), name.printTrimmed())) {
                maybeAddImport(FQN_KAFKA_PRODUCER_EXCEPTION, null, false);

                return JavaTemplate.builder("((KafkaProducerException)#{any()}).getFailedProducerRecord()")
                        .imports(FQN_KAFKA_PRODUCER_EXCEPTION)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-kafka-2"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), name);
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}

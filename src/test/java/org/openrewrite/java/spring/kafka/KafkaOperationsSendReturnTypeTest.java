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
package org.openrewrite.java.spring.kafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class KafkaOperationsSendReturnTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource("/META-INF/rewrite/spring-kafka-30.yml", "org.openrewrite.java.spring.kafka.UpgradeSpringKafka_3_0")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "kafka-clients-3",
            "spring-beans-5",
            "spring-context-5",
            "spring-core-5",
            "spring-kafka-2.9",
            "spring-messaging-5"
          ));
    }

    @DocumentExample
    @Test
    void changeKafkaOperationsSendReturnType() {
        //noinspection deprecation
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.kafka.core.KafkaOperations;
              import org.springframework.kafka.support.SendResult;
              import org.springframework.util.concurrent.ListenableFuture;
              import org.springframework.util.concurrent.ListenableFutureCallback;

              class Foo {
                  void bar(KafkaOperations<String, String> kafkaOperations) {
                      ListenableFuture<SendResult<String,String>> future = kafkaOperations.send("topic", "key", "value");
                      future.addCallback(new ListenableFutureCallback<>() {
                          @Override
                          public void onSuccess(SendResult<String, String> result) {
                              System.out.println(result.getRecordMetadata());
                          }

                          @Override
                          public void onFailure(Throwable ex) {
                              System.err.println(ex.getMessage());
                          }
                      });
                  }
              }
              """,
            """
              import org.springframework.kafka.core.KafkaOperations;
              import org.springframework.kafka.support.SendResult;

              import java.util.concurrent.CompletableFuture;

              class Foo {
                  void bar(KafkaOperations<String, String> kafkaOperations) {
                      CompletableFuture<SendResult<String,String>> future = kafkaOperations.send("topic", "key", "value");
                      future.whenComplete((SendResult<String, String> result, Throwable ex) -> {
                          if (ex == null) {
                              System.out.println(result.getRecordMetadata());
                          } else {
                              System.err.println(ex.getMessage());
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void changeKafkaTemplate() {
        //noinspection deprecation
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.kafka.core.KafkaTemplate;
              import org.springframework.kafka.support.SendResult;
              import org.springframework.util.concurrent.ListenableFuture;
              import org.springframework.util.concurrent.ListenableFutureCallback;

              class Foo {
                  void bar(KafkaTemplate<String, String> kafkaTemplate) {
                      ListenableFuture<SendResult<String,String>> future = kafkaTemplate.send("topic", "key", "value");
                      future.addCallback(new ListenableFutureCallback<>() {
                          @Override
                          public void onSuccess(SendResult<String, String> result) {
                              System.out.println(result.getRecordMetadata());
                          }

                          @Override
                          public void onFailure(Throwable ex) {
                              System.err.println(ex.getMessage());
                          }
                      });
                  }
              }
              """,
            """
              import org.springframework.kafka.core.KafkaTemplate;
              import org.springframework.kafka.support.SendResult;

              import java.util.concurrent.CompletableFuture;

              class Foo {
                  void bar(KafkaTemplate<String, String> kafkaTemplate) {
                      CompletableFuture<SendResult<String,String>> future = kafkaTemplate.send("topic", "key", "value");
                      future.whenComplete((SendResult<String, String> result, Throwable ex) -> {
                          if (ex == null) {
                              System.out.println(result.getRecordMetadata());
                          } else {
                              System.err.println(ex.getMessage());
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void noReplacementElsewhereYet() {
        //noinspection NullableProblems
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.util.concurrent.ListenableFuture;
              import org.springframework.util.concurrent.ListenableFutureCallback;

              class Foo {
                  void bar(ListenableFuture<String> future) {
                      future.addCallback(new ListenableFutureCallback<>() {
                          @Override
                          public void onSuccess(String result) {
                              System.out.println(result);
                          }

                          @Override
                          public void onFailure(Throwable ex) {
                              System.err.println(ex.getMessage());
                          }
                      });
                  }
              }
              """
          )
        );
    }
}

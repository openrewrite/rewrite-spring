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

class RemoveUsingCompletableFutureTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource("/META-INF/rewrite/spring-kafka-30.yml", "org.openrewrite.java.spring.kafka.UpgradeSpringKafka_3_0")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-kafka-2.9"));
    }

    @DocumentExample
    @Test
    void usingCompletableFuture() {
        //noinspection UnnecessaryLocalVariable
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.kafka.core.KafkaTemplate;
              import org.springframework.kafka.core.KafkaOperations;
              import org.springframework.kafka.core.KafkaOperations2;
              import org.springframework.kafka.support.SendResult;
              import java.util.concurrent.CompletableFuture;

              class Foo {
                  void bar(KafkaOperations<String, String> kafkaOperations) {
                      KafkaOperations2<String, String> k2 = kafkaOperations.usingCompletableFuture();
                  }
              }
              """,
            """
              import org.springframework.kafka.core.KafkaTemplate;
              import org.springframework.kafka.core.KafkaOperations;
              import org.springframework.kafka.support.SendResult;
              import java.util.concurrent.CompletableFuture;

              class Foo {
                  void bar(KafkaOperations<String, String> kafkaOperations) {
                      KafkaOperations<String, String> k2 = kafkaOperations;
                  }
              }
              """
          )
        );
    }
}

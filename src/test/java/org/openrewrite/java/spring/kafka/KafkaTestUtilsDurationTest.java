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

class KafkaTestUtilsDurationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new KafkaTestUtilsDuration())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "kafka-clients-3",
            "spring-kafka-test-2"
          ));
    }

    @Test
    @DocumentExample
    void adoptDuration() {
        //noinspection deprecation
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.kafka.clients.consumer.Consumer;
              import org.springframework.kafka.test.utils.KafkaTestUtils;
              
              class Foo {
                  void bar(Consumer<String, String> consumer) {
                      KafkaTestUtils.getRecords(consumer, 1000L);
                      KafkaTestUtils.getRecords(consumer, 1000L, 1);
                      KafkaTestUtils.getSingleRecord(consumer, "topic", 1000L);
                      KafkaTestUtils.getOneRecord("topic", "key", "value", 1, true, true, 1000L);
                  }
              }
              """,
            """
              import org.apache.kafka.clients.consumer.Consumer;
              import org.springframework.kafka.test.utils.KafkaTestUtils;
              
              import java.time.Duration;
              
              class Foo {
                  void bar(Consumer<String, String> consumer) {
                      KafkaTestUtils.getRecords(consumer, Duration.ofMillis(1000L));
                      KafkaTestUtils.getRecords(consumer, Duration.ofMillis(1000L), 1);
                      KafkaTestUtils.getSingleRecord(consumer, "topic", Duration.ofMillis(1000L));
                      KafkaTestUtils.getOneRecord("topic", "key", "value", 1, true, true, Duration.ofMillis(1000L));
                  }
              }
              """
          )
        );
    }
}

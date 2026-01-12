package org.openrewrite.java.spring.kafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeSpringKafkaErrorHandlersTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.spring.kafka.UpgradeSpringKafka_2_8_ErrorHandlers")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "kafka-clients",
            "spring-context",
            "spring-beans",
            "spring-kafka"
          ));
    }

    @Test
    void migratesSeekToCurrentErrorHandler() {
        rewriteRun(
          //language=java
          java(
            """
              import java.lang.Exception;
              import java.util.List;

              import org.apache.kafka.clients.consumer.Consumer;
              import org.apache.kafka.clients.consumer.ConsumerRecord;
              import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
              import org.springframework.kafka.listener.MessageListenerContainer;
              import org.springframework.kafka.listener.SeekToCurrentErrorHandler;

              class A {
                  private final SeekToCurrentErrorHandler handler = new SeekToCurrentErrorHandler();

                  void method(AbstractKafkaListenerContainerFactory factory) {
                      factory.setErrorHandler(handler);
                  }

                  void anotherMethod(
                    Exception exception,
                    List<ConsumerRecord<?,?>> records,
                    Consumer<?,?> consumer,
                    MessageListenerContainer container
                  ) {
                      handler.handle(exception, records, consumer, container);
                  }
              }
              """,
            """
              import java.lang.Exception;
              import java.util.List;

              import org.apache.kafka.clients.consumer.Consumer;
              import org.apache.kafka.clients.consumer.ConsumerRecord;
              import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
              import org.springframework.kafka.listener.DefaultErrorHandler;
              import org.springframework.kafka.listener.MessageListenerContainer;

              class A {
                  private final DefaultErrorHandler handler = new DefaultErrorHandler();

                  void method(AbstractKafkaListenerContainerFactory factory) {
                      factory.setCommonErrorHandler(handler);
                  }

                  void anotherMethod(
                    Exception exception,
                    List<ConsumerRecord<?,?>> records,
                    Consumer<?,?> consumer,
                    MessageListenerContainer container
                  ) {
                      handler.handleRemaining(exception, records, consumer, container);
                  }
              }
              """
          )
        );
    }
}

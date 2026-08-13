/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PreserveDefaultMessageListenerContainerStartupTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreserveDefaultMessageListenerContainerStartup())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(),
            "spring-data-mongodb-5.0",
            "spring-context-6"
          ));
    }

    @DocumentExample
    @Test
    void addsExplicitAutoStartupFalseToBean() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerConfiguration {

                  @Bean
                  DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                      DefaultMessageListenerContainer container =
                              new DefaultMessageListenerContainer(template);
                      return container;
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerConfiguration {

                  @Bean
                  DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                      DefaultMessageListenerContainer container =
                              new DefaultMessageListenerContainer(template);
                      container.setAutoStartup(false);
                      return container;
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesExplicitAutoStartupTrueUnchanged() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerConfiguration {

                  @Bean
                  DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                      DefaultMessageListenerContainer container =
                              new DefaultMessageListenerContainer(template);
                      container.setAutoStartup(true);
                      return container;
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesExplicitAutoStartupFalseUnchanged() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerConfiguration {

                  @Bean
                  DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                      DefaultMessageListenerContainer container =
                              new DefaultMessageListenerContainer(template);
                      container.setAutoStartup(false);
                      return container;
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesNonSpringManagedContainerUnchanged() {
        rewriteRun(
          java(
            """
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerFactory {

                  DefaultMessageListenerContainer create(MongoTemplate template) {
                      DefaultMessageListenerContainer container =
                              new DefaultMessageListenerContainer(template);
                      return container;
                  }
              }
              """
          )
        );
    }

    @Test
    void updatesDirectBeanReturn() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerConfiguration {

                  @Bean
                  DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                      return new DefaultMessageListenerContainer(template);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerConfiguration {

                  @Bean
                  DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                      DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                      container.setAutoStartup(false);
                      return container;
                  }
              }
              """
          )
        );
    }
    @Test
    void marksIndirectBeanReturnForManualReview() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerConfiguration {

                  private DefaultMessageListenerContainer createContainer(MongoTemplate template) {
                      return new DefaultMessageListenerContainer(template);
                  }

                  @Bean
                  DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                      return createContainer(template);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerConfiguration {

                  private DefaultMessageListenerContainer createContainer(MongoTemplate template) {
                      return new DefaultMessageListenerContainer(template);
                  }

                  /*~~(Unable to safely preserve DefaultMessageListenerContainer startup behavior. Configure setAutoStartup(false) manually.)~~>*/@Bean
                  DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                      return createContainer(template);
                  }
              }
              """
          )
        );
    }
    @Test
    void leavesConditionalAutoStartupConfigurationUnchanged() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.data.mongodb.core.MongoTemplate;
              import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

              class ListenerConfiguration {

                  boolean shouldStartAutomatically() {
                      return true;
                  }

                  @Bean
                  DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                      DefaultMessageListenerContainer container =
                              new DefaultMessageListenerContainer(template);

                      if (shouldStartAutomatically()) {
                          container.setAutoStartup(true);
                      }

                      return container;
                  }
              }
              """
          )
        );
    }
}

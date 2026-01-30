/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.batch;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddTransactionManagerToTaskletAndChunkTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddTransactionManagerToTaskletAndChunk())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4.3.+",
            "spring-batch-infrastructure-4.3.10",
            "spring-beans-4.3.30.RELEASE",
            "spring-context-4.3.30.RELEASE",
            "spring-tx-4.1.+"
          ));
    }

    @DocumentExample
    @Test
    void addTransactionManagerToTasklet() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  @Bean
                  Step myStep(Tasklet myTasklet) {
                      return stepBuilderFactory.get("myStep")
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  @Bean
                  Step myStep(Tasklet myTasklet, PlatformTransactionManager transactionManager) {
                      return stepBuilderFactory.get("myStep")
                              .tasklet(myTasklet, transactionManager)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void addTransactionManagerToChunkInt() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  @Bean
                  Step myStep() {
                      return stepBuilderFactory.get("myStep")
                              .<String, String>chunk(10)
                              .reader(reader())
                              .writer(writer())
                              .build();
                  }

                  private ItemWriter<String> writer() {
                      return null;
                  }

                  private ItemReader<String> reader() {
                      return null;
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  @Bean
                  Step myStep(PlatformTransactionManager transactionManager) {
                      return stepBuilderFactory.get("myStep")
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader())
                              .writer(writer())
                              .build();
                  }

                  private ItemWriter<String> writer() {
                      return null;
                  }

                  private ItemReader<String> reader() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void addTransactionManagerToChunkCompletionPolicy() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.batch.repeat.CompletionPolicy;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  @Bean
                  Step myStep() {
                      return stepBuilderFactory.get("myStep")
                              .<String, String>chunk(completionPolicy())
                              .reader(reader())
                              .writer(writer())
                              .build();
                  }

                  private CompletionPolicy completionPolicy() {
                      return null;
                  }

                  private ItemWriter<String> writer() {
                      return null;
                  }

                  private ItemReader<String> reader() {
                      return null;
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.batch.repeat.CompletionPolicy;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  @Bean
                  Step myStep(PlatformTransactionManager transactionManager) {
                      return stepBuilderFactory.get("myStep")
                              .<String, String>chunk(completionPolicy(), transactionManager)
                              .reader(reader())
                              .writer(writer())
                              .build();
                  }

                  private CompletionPolicy completionPolicy() {
                      return null;
                  }

                  private ItemWriter<String> writer() {
                      return null;
                  }

                  private ItemReader<String> reader() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void existingTransactionManagerParameter() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  @Bean
                  Step myStep(Tasklet myTasklet, PlatformTransactionManager txManager) {
                      return stepBuilderFactory.get("myStep")
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  @Bean
                  Step myStep(Tasklet myTasklet, PlatformTransactionManager txManager) {
                      return stepBuilderFactory.get("myStep")
                              .tasklet(myTasklet, txManager)
                              .build();
                  }
              }
              """
          )
        );
    }
}

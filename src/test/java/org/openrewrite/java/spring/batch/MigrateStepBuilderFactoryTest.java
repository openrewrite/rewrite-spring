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
package org.openrewrite.java.spring.batch;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateStepBuilderFactoryTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateStepBuilderFactory())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4.3",
            "spring-batch-infrastructure-4.3",
            "spring-beans-4.3.30.RELEASE",
            "spring-context-4.3.30.RELEASE"
          ));
    }

    @DocumentExample
    @Test
    void replaceStepBuilderFactoryWithTasket() {
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
                      return this.stepBuilderFactory.get("myStep")
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Step myStep(Tasklet myTasklet, JobRepository jobRepository) {
                      return new StepBuilder("myStep", jobRepository)
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/discussions/6016")
    @Test
    void genericsTypeProblem() {
        rewriteRun(
          spec -> spec.recipe(new MigrateStepBuilderFactory()),
          java(
            // language=java
            """
              import org.springframework.batch.core.step.builder.StepBuilder;

              class MyConfig {

                  void hello() {
                      new StepBuilder("myStep")
                              .startLimit(123)
                              .chunk(123);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceMethodInjectedStepBuilderFactoryWithTasklet() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Step myStep(StepBuilderFactory stepBuilderFactory, Tasklet myTasklet, StepBuilderFactory anotherFactory) {
                      return stepBuilderFactory.get("myStep")
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Step myStep(Tasklet myTasklet, JobRepository jobRepository) {
                      return new StepBuilder("myStep", jobRepository)
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceStepBuilderFactoryWithChunk() {
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
                      return this.stepBuilderFactory.get("myStep")
                              .<String, String> chunk(10)
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
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Step myStep(JobRepository jobRepository) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String> chunk(10)
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
    void replaceStepBuilderFactoryWithCompletionPolicyChunk() {
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
                      return this.stepBuilderFactory.get("myStep")
                              .<String, String> chunk(completionPolicy())
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
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.batch.repeat.CompletionPolicy;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Step myStep(JobRepository jobRepository) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String> chunk(completionPolicy())
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
}

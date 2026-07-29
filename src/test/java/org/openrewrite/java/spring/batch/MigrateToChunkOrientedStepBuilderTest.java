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
package org.openrewrite.java.spring.batch;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateToChunkOrientedStepBuilderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateToChunkOrientedStepBuilder())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-5.+",
            "spring-batch-infrastructure-5.+",
            "spring-beans-5.+",
            "spring-core-5.+",
            "spring-retry-2.+",
            "spring-tx-5.+"
          ));
    }

    @DocumentExample
    @Test
    void moveTransactionManagerOutOfChunk() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10)
                              .transactionManager(transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void faultTolerantRetryAndSkipAreSupported() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemProcessor;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemProcessor<String, String> processor, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .processor(processor)
                              .writer(writer)
                              .faultTolerant()
                              .retry(IllegalStateException.class)
                              .retryLimit(3)
                              .skip(IllegalArgumentException.class)
                              .skipLimit(5)
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemProcessor;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemProcessor<String, String> processor, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10)
                              .transactionManager(transactionManager)
                              .reader(reader)
                              .processor(processor)
                              .writer(writer)
                              .faultTolerant()
                              .retry(IllegalStateException.class)
                              .retryLimit(3)
                              .skip(IllegalArgumentException.class)
                              .skipLimit(5)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void singleLineChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager tx,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository).<String, String>chunk(10, tx).reader(reader).writer(writer).build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager tx,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository).<String, String>chunk(10).transactionManager(tx).reader(reader).writer(writer).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void typedListenerIsSupported() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.StepExecutionListener;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer, StepExecutionListener listener) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .listener(listener)
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.StepExecutionListener;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer, StepExecutionListener listener) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10)
                              .transactionManager(transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .listener(listener)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void backOffPolicyHasNoEquivalent() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.retry.backoff.FixedBackOffPolicy;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .faultTolerant()
                              .backOffPolicy(new FixedBackOffPolicy())
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void retryPolicyHasNoEquivalent() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.retry.policy.SimpleRetryPolicy;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .faultTolerant()
                              .retryPolicy(new SimpleRetryPolicy(3))
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void retryListenerIsSilentlyDroppedByTheNewModel() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.retry.RetryListener;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer, RetryListener listener) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .faultTolerant()
                              .listener(listener)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void annotatedPojoListenerIsSilentlyDroppedByTheNewModel() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer, Object listener) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .listener(listener)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void plainTaskExecutorIsNotAssignableToAsyncTaskExecutor() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.core.task.TaskExecutor;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer, TaskExecutor taskExecutor) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .taskExecutor(taskExecutor)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void asyncTaskExecutorIsSupported() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.core.task.SimpleAsyncTaskExecutor;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .taskExecutor(new SimpleAsyncTaskExecutor())
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.core.task.SimpleAsyncTaskExecutor;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10)
                              .transactionManager(transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .taskExecutor(new SimpleAsyncTaskExecutor())
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void readerIsTransactionalQueueHasNoEquivalent() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .readerIsTransactionalQueue()
                              .writer(writer)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void noRollbackHasNoEquivalent() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .faultTolerant()
                              .noRollback(IllegalStateException.class)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void chainNotEndingInBuildIsLeftAlone() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.SimpleStepBuilder;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      SimpleStepBuilder<String, String> builder = new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager);
                      return builder.reader(reader).writer(writer).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void taskletStepReturnTypeIsLeftAlone() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.core.step.tasklet.TaskletStep;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  TaskletStep myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                     ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void taskletStepVariableIsLeftAlone() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.core.step.tasklet.TaskletStep;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      TaskletStep step = new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .build();
                      return step;
                  }
              }
              """
          )
        );
    }

    @Test
    void wiredIntoSpringBatch5To6Migration() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.batch.SpringBatch5To6Migration"),
          // language=java
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10, transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.Step;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.infrastructure.item.ItemReader;
              import org.springframework.batch.infrastructure.item.ItemWriter;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(10)
                              .transactionManager(transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void completionPolicyChunkHasNoEquivalent() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.item.ItemReader;
              import org.springframework.batch.item.ItemWriter;
              import org.springframework.batch.repeat.policy.SimpleCompletionPolicy;
              import org.springframework.transaction.PlatformTransactionManager;

              class MyJobConfig {
                  Step myStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              ItemReader<String> reader, ItemWriter<String> writer) {
                      return new StepBuilder("myStep", jobRepository)
                              .<String, String>chunk(new SimpleCompletionPolicy(10), transactionManager)
                              .reader(reader)
                              .writer(writer)
                              .build();
                  }
              }
              """
          )
        );
    }
}

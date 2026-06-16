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
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class MigrateJobBuilderFactoryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJobBuilderFactory())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4.+",
            "spring-batch-infrastructure-4.+",
            "spring-beans-5.+",
            "spring-core-5.+",
            "spring-context-5.+"));
    }

    @DocumentExample
    @Test
    void replaceAutowiredJobBuilderFactory() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Autowired
                  private JobBuilderFactory jobBuilderFactory;

                  @Bean
                  Job myJob(Step step) {
                      return this.jobBuilderFactory.get("myJob")
                          .start(step)
                          .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.job.builder.JobBuilder;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Job myJob(Step step, JobRepository jobRepository) {
                      return new JobBuilder("myJob", jobRepository)
                          .start(step)
                          .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeCurrentApi() {
        rewriteRun(
          // This test's source is already the migrated (Spring Batch 5) API, so it must be
          // parsed against Spring Batch 5 rather than the class default of Spring Batch 4.
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-5.+",
            "spring-batch-infrastructure-5.+",
            "spring-beans-5.+",
            "spring-core-5.+",
            "spring-context-5.+")),
          // language=java
          java(
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.job.builder.JobBuilder;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Job myJob(Step step, JobRepository jobRepository) {
                      return new JobBuilder("myJob", jobRepository)
                          .start(step)
                          .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceJobBuilderFactory() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Job myJob(JobBuilderFactory jobBuilderFactory, Step step) {
                      return jobBuilderFactory.get("myJob")
                          .start(step)
                          .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.job.builder.JobBuilder;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Job myJob(Step step, JobRepository jobRepository) {
                      return new JobBuilder("myJob", jobRepository)
                          .start(step)
                          .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveFieldWhenGettersOrSettersReferenceIt() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private JobBuilderFactory jobBuilderFactory;

                  public MyJobConfig(JobBuilderFactory jobBuilderFactory, String other) {
                      this.jobBuilderFactory = jobBuilderFactory;
                  }

                  public JobBuilderFactory getJobBuilderFactory() {
                      return jobBuilderFactory;
                  }

                  public void setJobBuilderFactory(JobBuilderFactory jobBuilderFactory) {
                      this.jobBuilderFactory = jobBuilderFactory;
                  }

                  @Bean
                  Job myJob(Step step) {
                      return this.jobBuilderFactory.get("myJob")
                          .start(step)
                          .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
              import org.springframework.batch.core.job.builder.JobBuilder;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private JobBuilderFactory jobBuilderFactory;

                  public MyJobConfig(JobBuilderFactory jobBuilderFactory, String other) {
                      this.jobBuilderFactory = jobBuilderFactory;
                  }

                  public JobBuilderFactory getJobBuilderFactory() {
                      return jobBuilderFactory;
                  }

                  public void setJobBuilderFactory(JobBuilderFactory jobBuilderFactory) {
                      this.jobBuilderFactory = jobBuilderFactory;
                  }

                  @Bean
                  Job myJob(Step step, JobRepository jobRepository) {
                      return new JobBuilder("myJob", jobRepository)
                          .start(step)
                          .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void cleanUpConstructorBodyWhenParameterRemoved() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private JobBuilderFactory jobBuilderFactory;
                  private String other;

                  public MyJobConfig(JobBuilderFactory jobBuilderFactory, String other) {
                      this.jobBuilderFactory = jobBuilderFactory;
                      this.other = other;
                  }

                  @Bean
                  Job myJob(Step step) {
                      return this.jobBuilderFactory.get("myJob")
                          .start(step)
                          .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.job.builder.JobBuilder;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {
                  private String other;

                  public MyJobConfig(String other) {
                      this.other = other;
                  }

                  @Bean
                  Job myJob(Step step, JobRepository jobRepository) {
                      return new JobBuilder("myJob", jobRepository)
                          .start(step)
                          .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void localVariableShadowingFieldNameDoesNotPreserveField() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private JobBuilderFactory jobBuilderFactory;

                  void unrelatedHelper() {
                      String jobBuilderFactory = "not the field";
                      System.out.println(jobBuilderFactory);
                  }

                  @Bean
                  Job myJob(Step step) {
                      return this.jobBuilderFactory.get("myJob")
                          .start(step)
                          .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.job.builder.JobBuilder;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  void unrelatedHelper() {
                      String jobBuilderFactory = "not the field";
                      System.out.println(jobBuilderFactory);
                  }

                  @Bean
                  Job myJob(Step step, JobRepository jobRepository) {
                      return new JobBuilder("myJob", jobRepository)
                          .start(step)
                          .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceJobBuilderFactoryInsideConstructor() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private JobBuilderFactory jobBuilderFactory;

                  public MyJobConfig(JobBuilderFactory jobBuilderFactory) {
                      this.jobBuilderFactory = jobBuilderFactory;
                  }

                  @Bean
                  Job myJob(JobBuilderFactory jobBuilderFactory, Step step) {
                      return jobBuilderFactory.get("myJob")
                          .start(step)
                          .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.job.builder.JobBuilder;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Job myJob(Step step, JobRepository jobRepository) {
                      return new JobBuilder("myJob", jobRepository)
                          .start(step)
                          .build();
                  }
              }
              """
          )
        );
    }

}

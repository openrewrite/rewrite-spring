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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class MigrateStepBuilderFactoryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateStepBuilderFactory())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4.3.+",
            "spring-batch-infrastructure-4.3.10",
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
    void removeStepBuilderFactoryConstructorParameterAndBodyAssignment() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private StepBuilderFactory stepBuilderFactory;
                  private String other;

                  public MyJobConfig(StepBuilderFactory stepBuilderFactory, String other) {
                      this.stepBuilderFactory = stepBuilderFactory;
                      this.other = other;
                  }

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
                  private String other;

                  public MyJobConfig(String other) {
                      this.other = other;
                  }

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
    void preserveStepBuilderFactoryFieldWhenGettersOrSettersReferenceIt() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private StepBuilderFactory stepBuilderFactory;

                  public MyJobConfig(StepBuilderFactory stepBuilderFactory, String other) {
                      this.stepBuilderFactory = stepBuilderFactory;
                  }

                  public StepBuilderFactory getStepBuilderFactory() {
                      return stepBuilderFactory;
                  }

                  public void setStepBuilderFactory(StepBuilderFactory stepBuilderFactory) {
                      this.stepBuilderFactory = stepBuilderFactory;
                  }

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
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private StepBuilderFactory stepBuilderFactory;

                  public MyJobConfig(StepBuilderFactory stepBuilderFactory, String other) {
                      this.stepBuilderFactory = stepBuilderFactory;
                  }

                  public StepBuilderFactory getStepBuilderFactory() {
                      return stepBuilderFactory;
                  }

                  public void setStepBuilderFactory(StepBuilderFactory stepBuilderFactory) {
                      this.stepBuilderFactory = stepBuilderFactory;
                  }

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

    @Test
    void keepConstructorBodyWhenStepBuilderFactoryWasTheOnlyParameter() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private final StepBuilderFactory stepBuilderFactory;

                  MyJobConfig(StepBuilderFactory stepBuilderFactory) {
                      this.stepBuilderFactory = stepBuilderFactory;
                      init();
                  }

                  private void init() {
                  }

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
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  MyJobConfig() {
                      init();
                  }

                  private void init() {
                  }

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
    void reuseExistingJobRepositoryParameter() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Step myStep(StepBuilderFactory steps, JobRepository repository, Tasklet myTasklet) {
                      return steps.get("myStep")
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
                  Step myStep(JobRepository repository, Tasklet myTasklet) {
                      return new StepBuilder("myStep", repository)
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> {
                J.MethodDeclaration myStep = (J.MethodDeclaration) cu.getClasses().getFirst().getBody().getStatements().getFirst();
                assertThat(myStep.getMethodType().getParameterNames()).containsExactly("repository", "myTasklet");
                assertThat(myStep.getMethodType().getParameterTypes())
                  .extracting(t -> ((JavaType.FullyQualified) t).getFullyQualifiedName())
                  .containsExactly("org.springframework.batch.core.repository.JobRepository", "org.springframework.batch.core.step.tasklet.Tasklet");
            })
          )
        );
    }

    @Test
    void leaveHelperMethodsWithoutJobRepositoryUnchanged() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private final StepBuilderFactory stepBuilderFactory;

                  MyJobConfig(StepBuilderFactory stepBuilderFactory) {
                      this.stepBuilderFactory = stepBuilderFactory;
                  }

                  @Bean
                  Step firstStep(Tasklet myTasklet) {
                      return step("first", myTasklet);
                  }

                  @Bean
                  Step secondStep(Tasklet myTasklet) {
                      return step("second", myTasklet);
                  }

                  private Step step(String name, Tasklet tasklet) {
                      return stepBuilderFactory.get(name)
                              .tasklet(tasklet)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void helperMethodUsesJobRepositoryField() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private final StepBuilderFactory stepBuilderFactory;
                  private final JobRepository repository;

                  MyJobConfig(StepBuilderFactory stepBuilderFactory, JobRepository repository) {
                      this.stepBuilderFactory = stepBuilderFactory;
                      this.repository = repository;
                  }

                  @Bean
                  Step firstStep(Tasklet myTasklet) {
                      return step("first", myTasklet);
                  }

                  private Step step(String name, Tasklet tasklet) {
                      return stepBuilderFactory.get(name)
                              .tasklet(tasklet)
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
                  private final JobRepository repository;

                  MyJobConfig(JobRepository repository) {
                      this.repository = repository;
                  }

                  @Bean
                  Step firstStep(Tasklet myTasklet) {
                      return step("first", myTasklet);
                  }

                  private Step step(String name, Tasklet tasklet) {
                      return new StepBuilder(name, repository)
                              .tasklet(tasklet)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateStepAndJobBuilderFactoriesInSameMethod() {
        // language=java
        rewriteRun(
          spec -> spec.recipes(new MigrateJobBuilderFactory(), new MigrateStepBuilderFactory()),
          java(
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Job myJob(JobBuilderFactory jobs, StepBuilderFactory steps, Tasklet myTasklet) {
                      return jobs.get("myJob")
                              .start(steps.get("myStep").tasklet(myTasklet).build())
                              .build();
                  }
              }
              """,
            """
              import org.springframework.batch.core.Job;
              import org.springframework.batch.core.job.builder.JobBuilder;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  Job myJob(Tasklet myTasklet, JobRepository jobRepository) {
                      return new JobBuilder("myJob", jobRepository)
                              .start(new StepBuilder("myStep", jobRepository).tasklet(myTasklet).build())
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveFieldReferencedFromFieldInitializer() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.function.Supplier;

              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  private final Supplier<StepBuilderFactory> factorySupplier = () -> stepBuilderFactory;

                  @Bean
                  Step myStep(Tasklet myTasklet) {
                      return stepBuilderFactory.get("myStep")
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """,
            """
              import java.util.function.Supplier;

              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.builder.StepBuilder;
              import org.springframework.batch.core.step.tasklet.Tasklet;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Autowired
                  private StepBuilderFactory stepBuilderFactory;

                  private final Supplier<StepBuilderFactory> factorySupplier = () -> stepBuilderFactory;

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
    void migrateStepBuiltInConstructorWithJobRepositoryParameter() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.Step;
              import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.tasklet.Tasklet;

              class MyJobConfig {

                  private final Step step;

                  MyJobConfig(StepBuilderFactory steps, JobRepository repository, Tasklet myTasklet) {
                      this.step = steps.get("myStep")
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

              class MyJobConfig {

                  private final Step step;

                  MyJobConfig(JobRepository repository, Tasklet myTasklet) {
                      this.step = new StepBuilder("myStep", repository)
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void avoidJobRepositoryNameClash() {
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
                  Step myStep(StepBuilderFactory stepBuilderFactory, Tasklet myTasklet) {
                      String jobRepository = "unrelated";
                      return stepBuilderFactory.get(jobRepository)
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
                  Step myStep(Tasklet myTasklet, JobRepository jobRepository1) {
                      String jobRepository = "unrelated";
                      return new StepBuilder(jobRepository, jobRepository1)
                              .tasklet(myTasklet)
                              .build();
                  }
              }
              """
          )
        );
    }
}

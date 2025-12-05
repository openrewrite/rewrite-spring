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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJobStepTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
		spec.recipe(new MigrateJobStep())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
			"spring-batch-core-4.3.10",
			"spring-batch-infrastructure-4.3.10",
			"spring-beans-4.3.30.RELEASE",
			"spring-context-4.3.30.RELEASE"
          ));
    }

    @DocumentExample
    @Test
    void jobLauncherInjectedWithBeanAnnotation() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.launch.JobLauncher;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.Step;
              import org.springframework.batch.core.step.job.JobStep;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  JobStep myStep(JobRepository jobRepository, JobLauncher jobLauncher) {
                      JobStep jobStep = new JobStep(jobRepository);
                      jobStep.setJobLauncher(jobLauncher);
                      return jobStep;
                  }
              }
              """,
            """
              import org.springframework.batch.core.launch.JobOperator;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.Step;
              import org.springframework.batch.core.step.job.JobStep;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Bean
                  JobStep myStep(JobRepository jobRepository, JobOperator jobOperator) {
                      JobStep jobStep = new JobStep(jobRepository);
                      jobStep.setJobOperator(jobOperator);
                      return jobStep;
                  }
              }
              """
          )
        );
    }

    @Test
    void jobLauncherInjectedWithAutowiredAnnotation() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.launch.JobLauncher;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.Step;
              import org.springframework.batch.core.step.job.JobStep;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  @Autowired
                  private JobLauncher jobLauncher;

                  @Bean
                  JobStep myStep(JobRepository jobRepository) {
                      JobStep jobStep = new JobStep(jobRepository);
                      jobStep.setJobLauncher(jobLauncher);
                      return jobStep;
                  }
              }
              """,
            """
             import org.springframework.batch.core.launch.JobOperator;
             import org.springframework.batch.core.repository.JobRepository;
             import org.springframework.batch.core.step.Step;
             import org.springframework.batch.core.step.job.JobStep;
             import org.springframework.context.annotation.Bean;

             class MyJobConfig {

                 @Bean
                 JobStep myStep(JobRepository jobRepository, JobOperator jobOperator) {
                     JobStep jobStep = new JobStep(jobRepository);
                     jobStep.setJobOperator(jobOperator);
                     return jobStep;
                 }
             }
             """
          )
        );
    }

    @Test
    void jobLauncherInjectedWithConfigurationConstructor() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.launch.JobLauncher;
              import org.springframework.batch.core.repository.JobRepository;
              import org.springframework.batch.core.step.Step;
              import org.springframework.batch.core.step.job.JobStep;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;

              class MyJobConfig {

                  private final JobLauncher jobLauncher;

                  public MyJobConfig(JobLauncher jobLauncher) {
                      this.jobLauncher = jobLauncher;
                  }

                  @Bean
                  JobStep myStep(JobRepository jobRepository) {
                      JobStep jobStep = new JobStep(jobRepository);
                      jobStep.setJobLauncher(jobLauncher);
                      return jobStep;
                  }
              }
              """,
            """
             import org.springframework.batch.core.launch.JobOperator;
             import org.springframework.batch.core.repository.JobRepository;
             import org.springframework.batch.core.step.Step;
             import org.springframework.batch.core.step.job.JobStep;
             import org.springframework.context.annotation.Bean;

             class MyJobConfig {

                 @Bean
                 JobStep myStep(JobRepository jobRepository, JobOperator jobOperator) {
                     JobStep jobStep = new JobStep(jobRepository);
                     jobStep.setJobOperator(jobOperator);
                     return jobStep;
                 }
             }
             """
          )
        );
    }
}

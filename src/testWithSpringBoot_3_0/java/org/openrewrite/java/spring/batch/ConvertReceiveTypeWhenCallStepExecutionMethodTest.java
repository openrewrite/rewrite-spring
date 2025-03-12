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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ConvertReceiveTypeWhenCallStepExecutionMethodTest  implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-batch-core-4", "spring-batch-infrastructure", "spring-beans",  "mockito-core-5.+"))
          .recipe(new ConvertReceiveTypeWhenCallStepExecutionMethod());
    }

    @DocumentExample
    @Test
    void test1() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package test;

              import org.springframework.batch.core.JobExecution;
              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {


                  private void populateJobMetrics(JobExecution jobExecution) {
                    jobExecution.getStepExecutions().stream().map(StepExecution::getRollbackCount).mapToLong(Integer::longValue).sum();
                  }

              }
              """,
            """
              package test;

              import org.springframework.batch.core.JobExecution;
              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {


                  private void populateJobMetrics(JobExecution jobExecution) {
                    jobExecution.getStepExecutions().stream().map(_stepExecution -> (int) _stepExecution.getRollbackCount()).mapToLong(Integer::longValue).sum();
                  }

              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void test2() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {


                  private void populateJobMetrics(StepExecution stepExecution) {
                    int v = stepExecution.getRollbackCount();
                  }

                  private int populateJobMetrics2(StepExecution stepExecution) {
                   return stepExecution.getRollbackCount();
                  }

              }
              """,
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {


                  private void populateJobMetrics(StepExecution stepExecution) {
                    int v = (int) stepExecution.getRollbackCount();
                  }

                  private int populateJobMetrics2(StepExecution stepExecution) {
                   return (int) stepExecution.getRollbackCount();
                  }

              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void test3() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {


                  private void populateJobMetrics(StepExecution stepExecution) {
                    set(stepExecution.getRollbackCount());
                  }

                  private void set(int i) {
                  }

              }
              """,
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {


                  private void populateJobMetrics(StepExecution stepExecution) {
                    set((int) stepExecution.getRollbackCount());
                  }

                  private void set(int i) {
                  }

              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void test4() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              import org.mockito.Mockito;
              public class ProfileUpdateWriter {


                  private void populateJobMetrics(StepExecution stepExecution) {
                    Mockito.when(stepExecution.getRollbackCount()).thenReturn(1);
                  }



              }
              """,
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              import org.mockito.Mockito;
              public class ProfileUpdateWriter {


                  private void populateJobMetrics(StepExecution stepExecution) {
                    Mockito.when(stepExecution.getRollbackCount()).thenReturn((long) 1);
                  }



              }
              """
          )
        );
    }
}

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

class ConvertReceiveTypeWhenCallStepExecutionMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4",
            "spring-batch-infrastructure-4",
            "spring-beans-5",
            "mockito-core-5.+"))
          .recipe(new ConvertReceiveTypeWhenCallStepExecutionMethod());
    }

    @DocumentExample
    @Test
    void methodReferenceToLambda() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.JobExecution;
              import org.springframework.batch.core.StepExecution;
              import java.util.function.Function;

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
              import java.util.function.Function;

              public class ProfileUpdateWriter {

                  private void populateJobMetrics(JobExecution jobExecution) {
                    jobExecution.getStepExecutions().stream().map(stepExecution -> (int) stepExecution.getRollbackCount()).mapToLong(Integer::longValue).sum();
                  }

              }
              """
          )
        );
    }

    @Test
    void methodReferenceToIntFunctions() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import java.util.function.Function;
              import java.util.function.ToIntFunction;
              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  Function<StepExecution, Integer> readCount = StepExecution::getReadCount;
                  ToIntFunction<StepExecution> writeCount = StepExecution::getWriteCount;
              }
              """,
            """
              package test;

              import java.util.function.Function;
              import java.util.function.ToIntFunction;
              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  Function<StepExecution, Integer> readCount = stepExecution -> (int) stepExecution.getReadCount();
                  ToIntFunction<StepExecution> writeCount = stepExecution -> (int) stepExecution.getWriteCount();
              }
              """
          )
        );
    }

    @Test
    void castInAssignment() {
        //language=java
        rewriteRun(
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

    @Test
    void castInMethodInvocationArguments() {
        //language=java
        rewriteRun(
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

    @Test
    void changeMockitoWhen() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              import org.mockito.Mockito;
              public class ProfileUpdateWriter {

                  private void populateJobMetrics(StepExecution stepExecution) {
                    Mockito.when(stepExecution.getRollbackCount()).thenReturn(1);
                    Mockito.when(stepExecution.getReadCount()).thenReturn(1, 2, 3);
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
                    Mockito.when(stepExecution.getReadCount()).thenReturn((long) 1, (long) 2, (long) 3);
                  }

              }
              """
          )
        );
    }

    @Test
    void noChangeForUnrelatedMockitoStub() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              import org.mockito.Mockito;

              class ProfileUpdateWriter {
                  interface Counter {
                      int count();
                      int getRollbackCount();
                  }

                  void test(StepExecution stepExecution, Counter counter) {
                      int readCount = stepExecution.getReadCount();
                      long writeCount = stepExecution.getWriteCount();
                      Mockito.when(counter.count()).thenReturn(5);
                      Mockito.when(counter.getRollbackCount()).thenReturn(3);
                  }
              }
              """,
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              import org.mockito.Mockito;

              class ProfileUpdateWriter {
                  interface Counter {
                      int count();
                      int getRollbackCount();
                  }

                  void test(StepExecution stepExecution, Counter counter) {
                      int readCount = (int) stepExecution.getReadCount();
                      long writeCount = stepExecution.getWriteCount();
                      Mockito.when(counter.count()).thenReturn(5);
                      Mockito.when(counter.getRollbackCount()).thenReturn(3);
                  }
              }
              """
          )
        );
    }

    @Test
    void noTruncation() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  void foo(StepExecution stepExecution) {
                      long readCount = stepExecution.getReadCount() + 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void noCastForLongAssignment() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {

                  private void populateJobMetrics(StepExecution stepExecution) {
                    long v = stepExecution.getRollbackCount();
                  }

              }
              """
          )
        );
    }

    @Test
    void noCastForLongMethodArgument() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {

                  private void populateJobMetrics(StepExecution stepExecution) {
                    setLong(stepExecution.getRollbackCount());
                  }

                  private void setLong(long l) {
                  }

              }
              """
          )
        );
    }

    @Test
    void noCastForLongReturn() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {

                  private long getCount(StepExecution stepExecution) {
                    return stepExecution.getRollbackCount();
                  }

              }
              """
          )
        );
    }

    @Test
    void mixedIntAndLongAssignment() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {

                  private void populateJobMetrics(StepExecution stepExecution) {
                    int intVal = stepExecution.getRollbackCount();
                    long longVal = stepExecution.getReadCount();
                  }

              }
              """,
            """
              package test;

              import org.springframework.batch.core.StepExecution;
              public class ProfileUpdateWriter {

                  private void populateJobMetrics(StepExecution stepExecution) {
                    int intVal = (int) stepExecution.getRollbackCount();
                    long longVal = stepExecution.getReadCount();
                  }

              }
              """
          )
        );
    }

    @Test
    void unrelatedJavadoc() {
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.StepExecution;
              /**
                * @see String#concat(String)
                */
              class A {
                  private void populateJobMetrics(StepExecution stepExecution) {
                      int idontcare = stepExecution.getRollbackCount();
                  }
              }
              """,
            """
              import org.springframework.batch.core.StepExecution;
              /**
                * @see String#concat(String)
                */
              class A {
                  private void populateJobMetrics(StepExecution stepExecution) {
                      int idontcare = (int) stepExecution.getRollbackCount();
                  }
              }
              """
          )
        );
    }

    @Test
    void noNameClashForLambdaParameter() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.JobExecution;
              import org.springframework.batch.core.StepExecution;
              import java.util.function.Function;

              public class ProfileUpdateWriter {

                  private void populateJobMetrics(JobExecution jobExecution, StepExecution stepExecution) {
                    jobExecution.getStepExecutions().stream().map(StepExecution::getRollbackCount).mapToLong(Integer::longValue).sum();
                  }

              }
              """,
            """
              package test;

              import org.springframework.batch.core.JobExecution;
              import org.springframework.batch.core.StepExecution;
              import java.util.function.Function;

              public class ProfileUpdateWriter {

                  private void populateJobMetrics(JobExecution jobExecution, StepExecution stepExecution) {
                    jobExecution.getStepExecutions().stream().map(stepExecution1 -> (int) stepExecution1.getRollbackCount()).mapToLong(Integer::longValue).sum();
                  }

              }
              """
          )
        );
    }

    @Test
    void noChangeForToLongFunction() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import java.util.function.ToLongFunction;
              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  ToLongFunction<StepExecution> readCount = StepExecution::getReadCount;
              }
              """
          )
        );
    }

    @Test
    void noChangeForInstanceMethodReference() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import java.util.function.IntSupplier;
              import org.springframework.batch.core.StepExecution;

              class ProfileUpdaterWriter {
                  int count(StepExecution stepExecution) {
                      IntSupplier supplier = stepExecution::getReadCount;
                      return supplier.getAsInt();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForUnrelatedMethodReference() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;

              import java.util.function.Function;

              class ProfileUpdateWriter {
                  interface Counter {
                      int getRollbackCount();
                  }

                  Function<Counter, Integer> unrelated = Counter::getRollbackCount;

                  int test(StepExecution stepExecution) {
                      long readCount = stepExecution.getReadCount();
                      return 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenAlreadyCast() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  int test(StepExecution stepExecution) {
                      return (int) stepExecution.getReadCount();
                  }
              }
              """
          )
        );
    }

    @Test
    void castInArithmeticWithIntTarget() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  void test(StepExecution stepExecution) {
                      int total = stepExecution.getReadCount() + 1;
                  }
              }
              """,
            """
              package test;

              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  void test(StepExecution stepExecution) {
                      int total = ((int) stepExecution.getReadCount()) + 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void castInReassignment() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  void test(StepExecution stepExecution) {
                      int v;
                      v = stepExecution.getReadCount();
                  }
              }
              """,
            """
              package test;

              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  void test(StepExecution stepExecution) {
                      int v;
                      v = (int) stepExecution.getReadCount();
                  }
              }
              """
          )
        );
    }

    @Test
    void noCastInLambdaReturnForLongFunction() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import java.util.function.ToLongFunction;
              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  ToLongFunction<StepExecution> f = se -> { return se.getReadCount(); };
              }
              """
          )
        );
    }

    @Test
    void changeMockitoWhenWithStaticImport() {
        //language=java
        rewriteRun(
          java(
            """
              package test;

              import static org.mockito.Mockito.when;
              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  void test(StepExecution stepExecution) {
                      when(stepExecution.getReadCount()).thenReturn(1);
                  }
              }
              """,
            """
              package test;

              import static org.mockito.Mockito.when;
              import org.springframework.batch.core.StepExecution;

              class ProfileUpdateWriter {
                  void test(StepExecution stepExecution) {
                      when(stepExecution.getReadCount()).thenReturn((long) 1);
                  }
              }
              """
          )
        );
    }
}

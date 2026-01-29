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

class MigrateStepExecutionTimestampTypesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateStepExecutionTimestampTypes())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4.3.+",
            "spring-batch-infrastructure-4.3.10"
          ));
    }

    @DocumentExample
    @Test
    void stepExecutionGetStartTime() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.Date;
              import org.springframework.batch.core.StepExecution;

              class MyListener {
                  void afterStep(StepExecution stepExecution) {
                      Date startTime = stepExecution.getStartTime();
                      Date endTime = stepExecution.getEndTime();
                  }
              }
              """,
            """
              import java.time.LocalDateTime;
              import org.springframework.batch.core.StepExecution;

              class MyListener {
                  void afterStep(StepExecution stepExecution) {
                      LocalDateTime startTime = stepExecution.getStartTime();
                      LocalDateTime endTime = stepExecution.getEndTime();
                  }
              }
              """
          )
        );
    }

    @Test
    void jobExecutionTimestamps() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.Date;
              import org.springframework.batch.core.JobExecution;

              class MyListener {
                  void afterJob(JobExecution jobExecution) {
                      Date startTime = jobExecution.getStartTime();
                      Date endTime = jobExecution.getEndTime();
                      Date createTime = jobExecution.getCreateTime();
                      Date lastUpdated = jobExecution.getLastUpdated();
                  }
              }
              """,
            """
              import java.time.LocalDateTime;
              import org.springframework.batch.core.JobExecution;

              class MyListener {
                  void afterJob(JobExecution jobExecution) {
                      LocalDateTime startTime = jobExecution.getStartTime();
                      LocalDateTime endTime = jobExecution.getEndTime();
                      LocalDateTime createTime = jobExecution.getCreateTime();
                      LocalDateTime lastUpdated = jobExecution.getLastUpdated();
                  }
              }
              """
          )
        );
    }

    @Test
    void dateStillUsedElsewhereRetainsImport() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.Date;
              import org.springframework.batch.core.StepExecution;

              class MyListener {
                  void afterStep(StepExecution stepExecution) {
                      Date startTime = stepExecution.getStartTime();
                      Date today = new Date();
                  }
              }
              """,
            """
              import java.time.LocalDateTime;
              import java.util.Date;
              import org.springframework.batch.core.StepExecution;

              class MyListener {
                  void afterStep(StepExecution stepExecution) {
                      LocalDateTime startTime = stepExecution.getStartTime();
                      Date today = new Date();
                  }
              }
              """
          )
        );
    }

    @Test
    void alreadyLocalDateTimeNoChange() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.StepExecution;

              class MyListener {
                  void afterStep(StepExecution stepExecution) {
                      Object startTime = stepExecution.getStartTime();
                  }
              }
              """
          )
        );
    }

    @Test
    void noTimestampMethodNoChange() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.Date;
              import org.springframework.batch.core.StepExecution;

              class MyListener {
                  void afterStep(StepExecution stepExecution) {
                      Date today = new Date();
                      String name = stepExecution.getStepName();
                  }
              }
              """
          )
        );
    }
}

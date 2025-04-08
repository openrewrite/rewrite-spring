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

class JobParameterToStringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4.3.+", "spring-batch-infrastructure-4.3.+", "spring-beans-4.3.+"))
          .recipe(new JobParameterToString());
    }

    @DocumentExample
    @Test
    void insertGetValueBeforeToString() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.JobParameters;

              class Foo {
                  void foo(JobParameters jobParameters) throws Exception {
                      jobParameters.getParameters().get("inputFile").toString();
                  }
              }
              """,
            """
              import org.springframework.batch.core.JobParameters;

              class Foo {
                  void foo(JobParameters jobParameters) throws Exception {
                      jobParameters.getParameters().get("inputFile").getValue().toString();
                  }
              }
              """
          )
        );
    }
}

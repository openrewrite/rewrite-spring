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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringBatch5To6MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.batch.SpringBatch5To6Migration")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-5.2",
            "spring-batch-infrastructure-5.2"
          ));
    }

    @Test
    void moveIncrementersToJobParametersPackage() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.batch.core.launch.support.DataFieldMaxValueJobParametersIncrementer;
              import org.springframework.batch.core.launch.support.RunIdIncrementer;

              class MyJobConfig {
                  RunIdIncrementer runIdIncrementer;
                  DataFieldMaxValueJobParametersIncrementer dataFieldMaxValueIncrementer;
              }
              """,
            """
              import org.springframework.batch.core.job.parameters.DataFieldMaxValueJobParametersIncrementer;
              import org.springframework.batch.core.job.parameters.RunIdIncrementer;

              class MyJobConfig {
                  RunIdIncrementer runIdIncrementer;
                  DataFieldMaxValueJobParametersIncrementer dataFieldMaxValueIncrementer;
              }
              """
          )
        );
    }
}

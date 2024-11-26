/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

class UpgradeSkipPolicyParameterTypeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResource("/META-INF/rewrite/spring-batch-5.0.yml", "org.openrewrite.java.spring.batch.UpgradeSkipPolicyParameterType")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4.3.+",
            "spring-batch-infrastructure-4.3.+",
            "spring-beans-4.3.30.RELEASE",
            "spring-batch",
            "spring-boot",
            "spring-core",
            "spring-context"
          ));
    }

    @DocumentExample
    @Test
    void replaceParameter() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.step.skip.SkipLimitExceededException;
              import org.springframework.batch.core.step.skip.SkipPolicy;

              public class MySkipPolicy implements SkipPolicy {
                  @Override
                  public boolean shouldSkip(Throwable throwable, int skipCount) {
                      return true;
                  }
              }
              """,
            """
              import org.springframework.batch.core.step.skip.SkipLimitExceededException;
              import org.springframework.batch.core.step.skip.SkipPolicy;

              public class MySkipPolicy implements SkipPolicy {
                  @Override
                  public boolean shouldSkip(Throwable throwable, long skipCount) {
                      return true;
                  }
              }
              """
          )
        );
    }
}

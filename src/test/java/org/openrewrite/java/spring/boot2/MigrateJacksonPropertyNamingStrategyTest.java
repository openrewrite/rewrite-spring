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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJacksonPropertyNamingStrategyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_5")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(), "jackson-databind-2.19"));
    }

    @DocumentExample
    @Test
    void replaceSnakeCaseStrategyInJsonNamingAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;
              import com.fasterxml.jackson.databind.annotation.JsonNaming;

              @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
              class Test {
              }
              """,
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
              import com.fasterxml.jackson.databind.annotation.JsonNaming;

              @JsonNaming(SnakeCaseStrategy.class)
              class Test {
              }
              """
          )
        );
    }

    @Test
    void replaceSnakeCaseConstant() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;

              class Test {
                  PropertyNamingStrategy strategy = PropertyNamingStrategy.SNAKE_CASE;
              }
              """,
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategies;
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;

              class Test {
                  PropertyNamingStrategy strategy = PropertyNamingStrategies.SNAKE_CASE;
              }
              """
          )
        );
    }
}

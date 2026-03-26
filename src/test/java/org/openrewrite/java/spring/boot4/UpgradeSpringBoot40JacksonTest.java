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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeSpringBoot40JacksonTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new org.openrewrite.java.ChangeType(
            "org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer",
            "org.springframework.boot.autoconfigure.jackson.JsonMapperBuilderCustomizer",
            null))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-autoconfigure-3"
          ));
    }

    @DocumentExample
    @Test
    void renameJackson2ObjectMapperBuilderCustomizer() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

              class Test {
                  Jackson2ObjectMapperBuilderCustomizer customizer;
              }
              """,
            """
              import org.springframework.boot.autoconfigure.jackson.JsonMapperBuilderCustomizer;

              class Test {
                  JsonMapperBuilderCustomizer customizer;
              }
              """
          )
        );
    }
}

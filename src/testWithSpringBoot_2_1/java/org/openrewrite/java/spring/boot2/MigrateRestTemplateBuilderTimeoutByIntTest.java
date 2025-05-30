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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateRestTemplateBuilderTimeoutByIntTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateRestTemplateBuilderTimeoutByInt())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot", "spring-web", "spring-core"));
    }

    @DocumentExample
    @Test
    void changeDeprecatedMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.web.client.RestTemplate;

              class Test {
                  RestTemplate template = new RestTemplateBuilder()
                          .setConnectTimeout(1)
                          .setReadTimeout(1)
                          .build();
              }
              """,
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.web.client.RestTemplate;
              
              import java.time.Duration;
              
              class Test {
                  RestTemplate template = new RestTemplateBuilder()
                          .setConnectTimeout(Duration.ofMillis(1))
                          .setReadTimeout(Duration.ofMillis(1))
                          .build();
              }
              """
          )
        );
    }

    @Test
    void doNotChangeCurrentApi() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.web.client.RestTemplate;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import java.time.Duration;
              class Test {
                  RestTemplate template = new RestTemplateBuilder()
                      .setConnectTimeout(Duration.ofMillis(1)).build();
              }
              """
          )
        );
    }
}

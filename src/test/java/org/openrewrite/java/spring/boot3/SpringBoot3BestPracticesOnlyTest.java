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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcTestJava;

class SpringBoot3BestPracticesOnlyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot3.SpringBoot3BestPracticesOnly")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.2", "spring-test-6.+"));
    }

    @Test
    void simplifyMediaTypeParseCallsOutsideController() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.2", "spring-core-6.+")),
          //language=java
          java(
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.MediaType;

              class GreetingClient {
                  void accept(HttpHeaders headers) {
                      headers.setContentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE));
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.MediaType;

              class GreetingClient {
                  void accept(HttpHeaders headers) {
                      headers.setContentType(MediaType.APPLICATION_JSON);
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyWebTestClientCallsInTestWithoutController() {
        rewriteRun(
          srcTestJava(
            //language=java
            java(
              """
                import org.springframework.test.web.reactive.server.WebTestClient;

                class GreetingTest {
                    WebTestClient webClient;

                    void greeting() {
                        webClient.get()
                          .uri("/greeting")
                          .exchange()
                          .expectStatus()
                          .isEqualTo(200);
                    }
                }
                """,
              """
                import org.springframework.test.web.reactive.server.WebTestClient;

                class GreetingTest {
                    WebTestClient webClient;

                    void greeting() {
                        webClient.get()
                          .uri("/greeting")
                          .exchange()
                          .expectStatus()
                          .isOk();
                    }
                }
                """
            )
          )
        );
    }
}

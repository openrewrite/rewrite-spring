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
package org.openrewrite.java.spring.http;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyWebTestClientCallsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new SimplifyWebTestClientCalls())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-web-6", "spring-test-6"));
    }

    @DocumentExample
    @Test
    void usesIsOkForIntStatus200() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.web.reactive.server.WebTestClient;

              class Test {
                  private final WebTestClient webClient = WebTestClient.bindToServer().build();
                  void someMethod() {
                    webClient
                        .post()
                        .uri("/some/url")
                        .bodyValue("someValue")
                        .exchange()
                        .expectStatus()
                        .isEqualTo(200);
                  }
              }
              """,
            """
              import org.springframework.test.web.reactive.server.WebTestClient;

              class Test {
                  private final WebTestClient webClient = WebTestClient.bindToServer().build();
                  void someMethod() {
                    webClient
                        .post()
                        .uri("/some/url")
                        .bodyValue("someValue")
                        .exchange()
                        .expectStatus()
                        .isOk();
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void doesNotUseIsOkForIntStatus300() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.web.reactive.server.WebTestClient;

              class Test {
                  private final WebTestClient webClient = WebTestClient.bindToServer().build();
                  void someMethod() {
                    webClient
                        .post()
                        .uri("/some/url")
                        .bodyValue("someValue")
                        .exchange()
                        .expectStatus()
                        .isEqualTo(300);
                  }
              }
              """
          )
        );
    }

    @Test
    void usesIsOkForHttpStatus200() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.web.reactive.server.WebTestClient;
              import org.springframework.http.HttpStatus;
              class Test {
                  private final WebTestClient webClient = WebTestClient.bindToServer().build();
                  void someMethod() {
                      webClient
                          .post()
                          .uri("/some/value")
                          .exchange()
                          .expectStatus()
                          .isEqualTo(HttpStatus.valueOf(200));
                  }
              }
              """,
            """
              import org.springframework.test.web.reactive.server.WebTestClient;
              class Test {
                  private final WebTestClient webClient = WebTestClient.bindToServer().build();
                  void someMethod() {
                      webClient
                          .post()
                          .uri("/some/value")
                          .exchange()
                          .expectStatus()
                          .isOk();
                  }
              }
              """
          )
        );
    }

    @Test
    @Disabled("Yet to be implemented")
    void doesNotUseIsOkForHttpStatus300() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.web.reactive.server.WebTestClient;
              import org.springframework.http.HttpStatus;
              class Test {
                  private final WebTestClient webClient = WebTestClient.bindToServer().build();
                  void someMethod() {
                      webClient
                          .post()
                          .uri("/some/value")
                          .exchange()
                          .expectStatus()
                          .isEqualTo(HttpStatus.valueOf(300));
                  }
              }
              """
          )
        );
    }

}

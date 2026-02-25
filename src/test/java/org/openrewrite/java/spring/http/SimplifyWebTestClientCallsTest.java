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
package org.openrewrite.java.spring.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class SimplifyWebTestClientCallsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new SimplifyWebTestClientCalls())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.+", "spring-test-6.+"))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-web-6", "spring-test-6"));
    }

    @DocumentExample
    @Test
    void replaceKotlinInt() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.springframework.test.web.reactive.server.WebTestClient

              class Test {
                  val webClient: WebTestClient = WebTestClient.bindToServer().build()
                  fun someMethod() {
                    webClient
                        .post()
                        .uri("/some/url")
                        .bodyValue("someValue")
                        .exchange()
                        .expectStatus()
                        .isEqualTo(200)
                  }
              }
              """,
            """
              import org.springframework.test.web.reactive.server.WebTestClient

              class Test {
                  val webClient: WebTestClient = WebTestClient.bindToServer().build()
                  fun someMethod() {
                    webClient
                        .post()
                        .uri("/some/url")
                        .bodyValue("someValue")
                        .exchange()
                        .expectStatus()
                        .isOk()
                  }
              }
              """
          )
        );
    }

    @CsvSource({
      "200,isOk()",
      "201,isCreated()",
      "202,isAccepted()",
      "204,isNoContent()",
      "302,isFound()",
      "303,isSeeOther()",
      "304,isNotModified()",
      "307,isTemporaryRedirect()",
      "308,isPermanentRedirect()",
      "400,isBadRequest()",
      "401,isUnauthorized()",
      "403,isForbidden()",
      "404,isNotFound()"
    })
    @ParameterizedTest
    void replacesAllIntStatusCodes(String httpStatus, String method) {
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
                        .isEqualTo(%s);
                  }
              }
              """.formatted(httpStatus),
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
                        .%s;
                  }
              }
              """.formatted(method)
          )
        );
    }

    @Test
    void doesNotReplaceUnspecificStatusCode() {
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
    void usesIsOkForHttpStatusValueOf200() {
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
    void usesIsOkForHttpStatusValueCodeOf200() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.web.reactive.server.WebTestClient;
              import org.springframework.http.HttpStatusCode;

              class Test {
                  private final WebTestClient webClient = WebTestClient.bindToServer().build();
                  void someMethod() {
                      webClient
                          .post()
                          .uri("/some/value")
                          .exchange()
                          .expectStatus()
                          .isEqualTo(HttpStatusCode.valueOf(200));
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

    @CsvSource({
      "OK,isOk()",
      "CREATED,isCreated()",
      "ACCEPTED,isAccepted()",
      "NO_CONTENT,isNoContent()",
      "FOUND,isFound()",
      "SEE_OTHER,isSeeOther()",
      "NOT_MODIFIED,isNotModified()",
      "TEMPORARY_REDIRECT,isTemporaryRedirect()",
      "PERMANENT_REDIRECT,isPermanentRedirect()",
      "BAD_REQUEST,isBadRequest()",
      "UNAUTHORIZED,isUnauthorized()",
      "FORBIDDEN,isForbidden()",
      "NOT_FOUND,isNotFound()"
    })
    @ParameterizedTest
    void usesIsOkForHttpStatusValue(String httpStatus, String method) {
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
                          .isEqualTo(HttpStatus.%s);
                  }
              }
              """.formatted(httpStatus),
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
                          .%s;
                  }
              }
              """.formatted(method)
          )
        );
    }

    @Test
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

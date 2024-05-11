package org.openrewrite.java.spring.http;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class SimplifyWebTestClientCallsTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
      spec
        .recipe(new SimplifyWebTestClientCalls())
        .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-core", "spring-web-6.+", "spring-boot-test"));
  }

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

  @Test
  void usesIsOkForHttpStatus200() {
    rewriteRun(
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
                    .bodyValue("someValue")
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
                    .accept("someValue")
                    .exchange()
                    .expectStatus()
                    .isOk();
              }
          }
          """
      )
    );
  }

}

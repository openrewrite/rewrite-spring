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
package org.openrewrite.java.spring.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

class FindApiCallsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindApiCalls())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-webflux-5.+",
            "spring-web-5.3",
            "spring-core-5.3",
            "spring-boot-3.5"));
    }

    @DocumentExample
    @Test
    void restTemplate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.client.RestTemplate;
              class Test {
                  RestTemplate restTemplate;
                  void test() {
                      restTemplate.getForObject(base() + "/getThing", String.class);
                      restTemplate.postForEntity(base() + "/postThing", null, String.class);
                  }

                  String base() {
                      return "https://base";
                  }
              }
              """,
            """
              import org.springframework.web.client.RestTemplate;
              class Test {
                  RestTemplate restTemplate;
                  void test() {
                      /*~~(GET base() + "/getThing")~~>*/restTemplate.getForObject(base() + "/getThing", String.class);
                      /*~~(POST base() + "/postThing")~~>*/restTemplate.postForEntity(base() + "/postThing", null, String.class);
                  }

                  String base() {
                      return "https://base";
                  }
              }
              """
          )
        );
    }

    @Test
    void webClient() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.reactive.function.client.WebClient;
              class Test {
                  WebClient.Builder webClientBuilder;
                  void test() {
                      webClientBuilder.build()
                        .get()
                        .uri(base() + "/getThing");

                      webClientBuilder.build()
                        .post()
                        .uri(base() + "/postThing");
                  }

                  String base() {
                      return "https://base";
                  }
              }
              """
          )
        );
    }

    @Test
    void restTemplateExchange() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.web.client.RestTemplate;
              class Test {
                  RestTemplate restTemplate;
                  void test(HttpMethod in) {
                      HttpMethod m = HttpMethod.GET;
                      restTemplate.exchange("/getThing", HttpMethod.GET, null, String.class);
                      restTemplate.exchange("/postThing", HttpMethod.POST, null, String.class);
                      restTemplate.exchange("/getThing", m, null, String.class);
                      restTemplate.exchange("/getThing", in, null, String.class);
                  }
              }
              """,
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.web.client.RestTemplate;
              class Test {
                  RestTemplate restTemplate;
                  void test(HttpMethod in) {
                      HttpMethod m = HttpMethod.GET;
                      /*~~(GET /getThing)~~>*/restTemplate.exchange("/getThing", HttpMethod.GET, null, String.class);
                      /*~~(POST /postThing)~~>*/restTemplate.exchange("/postThing", HttpMethod.POST, null, String.class);
                      /*~~(UNKNOWN /getThing)~~>*/restTemplate.exchange("/getThing", m, null, String.class);
                      /*~~(UNKNOWN /getThing)~~>*/restTemplate.exchange("/getThing", in, null, String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticallyImportedHttpMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.client.RestTemplate;
              import static org.springframework.http.HttpMethod.DELETE;
              class Test {
                  RestTemplate restTemplate;
                  void test() {
                      restTemplate.exchange("/deleteThing", DELETE, null, Void.class);
                  }
              }
              """,
            """
              import org.springframework.web.client.RestTemplate;
              import static org.springframework.http.HttpMethod.DELETE;
              class Test {
                  RestTemplate restTemplate;
                  void test() {
                      /*~~(DELETE /deleteThing)~~>*/restTemplate.exchange("/deleteThing", DELETE, null, Void.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotMatchTestSources() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.client.RestTemplate;
              class Test {
                  RestTemplate restTemplate;
                  void test() {
                      restTemplate.getForObject("/getThing", String.class);
                  }
              }
              """,
            sourceSpecs -> sourceSpecs.markers(JavaSourceSet.build("test", List.of()))
          )
        );
    }

    @Test
    void uri() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.web.client.RestTemplate;
              import java.net.URI;
              class Test {
                  RestTemplate restTemplate;
                  void test(URI in) {
                      restTemplate.exchange(URI.create("/getThing"), HttpMethod.GET, null, String.class);
                      restTemplate.getForObject(URI.create("/getThing"), String.class);
                      restTemplate.getForObject(in, String.class);
                  }
              }
              """,
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.web.client.RestTemplate;
              import java.net.URI;
              class Test {
                  RestTemplate restTemplate;
                  void test(URI in) {
                      /*~~(GET /getThing)~~>*/restTemplate.exchange(URI.create("/getThing"), HttpMethod.GET, null, String.class);
                      /*~~(GET /getThing)~~>*/restTemplate.getForObject(URI.create("/getThing"), String.class);
                      /*~~(GET in)~~>*/restTemplate.getForObject(in, String.class);
                  }
              }
              """
          )
        );
    }
}

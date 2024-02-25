/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.search;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Disabled
// TODO work in progress
class FindApiCallsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindApiCalls())
          .parser(JavaParser.fromJavaVersion().classpath("spring-webflux", "spring-web", "spring-boot"));
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
                      restTemplate.exchange("/getThing", HttpMethod.GET, String.class);
                      restTemplate.exchange("/getThing", m, String.class);
                      restTemplate.exchange("/getThing", in, String.class);
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
                      /*~~(GET /getThing)~~>*/restTemplate.exchange("/getThing", HttpMethod.GET, String.class);
                      /*~~(GET /getThing)~~>*/restTemplate.exchange("/getThing", m, String.class);
                      /*~~(UNKNOWN /getThing)~~>*/restTemplate.exchange("/getThing", in, String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void uriDataFlow() {
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
                      URI local = URI.create("/getThing");
                      restTemplate.exchange(URI.create("/getThing"), HttpMethod.GET, String.class);
                      restTemplate.getForObject(local, String.class);
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
                      URI local = URI.create("/getThing");
                      /*~~(GET /getThing)~~>*/restTemplate.exchange(URI.create("/getThing"), HttpMethod.GET, String.class);
                      /*~~(GET /getThing)~~>*/restTemplate.getForObject(local, String.class);
                      /*~~(GET UNKNOWN)~~>*/restTemplate.getForObject(in, String.class);
                  }
              }
              """
          )
        );
    }
}

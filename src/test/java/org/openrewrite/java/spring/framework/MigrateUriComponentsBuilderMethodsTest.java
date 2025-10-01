/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateUriComponentsBuilderMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new MigrateUriComponentsBuilderMethods())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-web-5"));
    }

    @DocumentExample
    @Test
    void migrateUriComponentsBuilderMethods() {
        rewriteRun(
          // language=java
          java(
            """
              import java.net.InetSocketAddress;
              import org.springframework.http.HttpRequest;
              import org.springframework.web.util.UriComponentsBuilder;

              class A {
                  void test() {
                      HttpRequest request;
                      InetSocketAddress inetSocketAddress;
                      UriComponentsBuilder.fromHttpRequest(request).queryParam("foo", "bar");
                      UriComponentsBuilder.parseForwardedFor(request, inetSocketAddress);
                  }
              }
              """,
            """
              import java.net.InetSocketAddress;
              import org.springframework.http.HttpRequest;
              import org.springframework.web.util.ForwardedHeaderUtils;

              class A {
                  void test() {
                      HttpRequest request;
                      InetSocketAddress inetSocketAddress;
                      ForwardedHeaderUtils.adaptFromForwardedHeaders(request.getURI(), request.getHeaders()).queryParam("foo", "bar");
                      ForwardedHeaderUtils.parseForwardedFor(request.getURI(), request.getHeaders(), inetSocketAddress);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateUriComponentsBuilderMethodsWhenInetSocketAddressIsNull() {
        rewriteRun(
          // language=java
          java(
            """
              import java.net.InetSocketAddress;
              import org.springframework.http.HttpRequest;
              import org.springframework.web.util.UriComponentsBuilder;

              class A {
                  void test() {
                      HttpRequest request;
                      InetSocketAddress inetSocketAddress;
                      UriComponentsBuilder.fromHttpRequest(request).queryParam("foo", "bar");
                      UriComponentsBuilder.parseForwardedFor(request, null);
                  }
              }
              """,
            """
              import java.net.InetSocketAddress;
              import org.springframework.http.HttpRequest;
              import org.springframework.web.util.ForwardedHeaderUtils;

              class A {
                  void test() {
                      HttpRequest request;
                      InetSocketAddress inetSocketAddress;
                      ForwardedHeaderUtils.adaptFromForwardedHeaders(request.getURI(), request.getHeaders()).queryParam("foo", "bar");
                      ForwardedHeaderUtils.parseForwardedFor(request.getURI(), request.getHeaders(), null);
                  }
              }
              """
          )
        );
    }
}

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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceRestTemplateBuilderMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.boot3.ReplaceRestTemplateBuilderMethods")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-boot"));
    }

    @DocumentExample
    @Test
    void replacesSetConnectTimeout() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;

              class Example {
                  public void configure(RestTemplateBuilder builder) {
                      builder.setConnectTimeout(java.time.Duration.ofSeconds(10));
                  }
              }
              """,
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;

              class Example {
                  public void configure(RestTemplateBuilder builder) {
                      builder.connectTimeout(java.time.Duration.ofSeconds(10));
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesSetReadTimeout() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;

              class Example {
                  public void configure(RestTemplateBuilder builder) {
                      builder.setReadTimeout(java.time.Duration.ofSeconds(10));
                  }
              }
              """,
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;

              class Example {
                  public void configure(RestTemplateBuilder builder) {
                      builder.readTimeout(java.time.Duration.ofSeconds(10));
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesSetSslBundle() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.boot.ssl.SslBundle;

              class Example {
                  public void configure(RestTemplateBuilder builder, SslBundle sslBundle) {
                      builder.setSslBundle(sslBundle);
                  }
              }
              """,
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.boot.ssl.SslBundle;

              class Example {
                  public void configure(RestTemplateBuilder builder, SslBundle sslBundle) {
                      builder.sslBundle(sslBundle);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesRequestFactory() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.ClientHttpRequestFactory;

              class Example {
                  public void configure(RestTemplateBuilder builder, ClientHttpRequestFactory factory) {
                      builder.requestFactory(factory);
                  }
              }
              """,
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.ClientHttpRequestFactory;

              class Example {
                  public void configure(RestTemplateBuilder builder, ClientHttpRequestFactory factory) {
                      builder.requestFactoryBuilder(factory);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNothingWhenNoDeprecatedMethodsPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.web.client.RestTemplateBuilder;

              class Example {
                  public void configure(RestTemplateBuilder builder) {
                      builder.additionalCustomizers(customizer -> {});
                  }
              }
              """
          )
        );
    }
}

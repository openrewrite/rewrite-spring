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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class HttpComponentsClientHttpRequestFactoryConnectTimeoutTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.HttpComponentsClientHttpRequestFactoryConnectTimeout")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-beans-5.3", "spring-web-5.3"));
    }

    @DocumentExample
    @Test
    void addsCommentToSetConnectTimeoutOnly() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

              class Example {
                  void configure() {
                      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
                      factory.setConnectTimeout(5000);
                  }
              }
              """,
            """
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

              class Example {
                  void configure() {
                      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
                      /* TODO: `setConnectTimeout` was removed in Spring Framework 7.0. Set `ConnectionConfig.Builder.setConnectTimeout(Timeout)` on the connection manager when building the HttpClient; see https://hc.apache.org/httpcomponents-client-5.6.x/migration-guide/migration-to-classic.html and https://github.com/spring-projects/spring-framework/issues/35748 */
                      factory.setConnectTimeout(5000);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotDuplicateComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

              class Example {
                  void configure() {
                      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
                      // TODO: `setConnectTimeout` was removed in Spring Framework 7.0. Set `ConnectionConfig.Builder.setConnectTimeout(Timeout)` on the connection manager when building the HttpClient; see https://hc.apache.org/httpcomponents-client-5.6.x/migration-guide/migration-to-classic.html and https://github.com/spring-projects/spring-framework/issues/35748
                      factory.setConnectTimeout(5000);
                  }
              }
              """
          )
        );
    }

    @Test
    void commentsEvenWhenConnectionManagerIsPresent() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-beans-6", "spring-web-6", "httpclient5", "httpcore5")),
          //language=java
          java(
            """
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClients;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

              class Example {
                  HttpComponentsClientHttpRequestFactory requestFactory() {
                      PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
                      CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
                      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
                      factory.setConnectTimeout(2000);
                      return factory;
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
              import org.apache.hc.client5.http.impl.classic.HttpClients;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

              class Example {
                  HttpComponentsClientHttpRequestFactory requestFactory() {
                      PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
                      CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
                      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
                      /* TODO: `setConnectTimeout` was removed in Spring Framework 7.0. Set `ConnectionConfig.Builder.setConnectTimeout(Timeout)` on the connection manager when building the HttpClient; see https://hc.apache.org/httpcomponents-client-5.6.x/migration-guide/migration-to-classic.html and https://github.com/spring-projects/spring-framework/issues/35748 */
                      factory.setConnectTimeout(2000);
                      return factory;
                  }
              }
              """
          )
        );
    }

    @Test
    void addsCommentInKotlinSources() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(),
            "spring-web-5.3")),
          //language=kotlin
          kotlin(
            """
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory

              fun configure() {
                  val factory = HttpComponentsClientHttpRequestFactory()
                  factory.setConnectTimeout(5000)
              }
              """,
            """
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory

              fun configure() {
                  val factory = HttpComponentsClientHttpRequestFactory()
                  /* TODO: `setConnectTimeout` was removed in Spring Framework 7.0. Set `ConnectionConfig.Builder.setConnectTimeout(Timeout)` on the connection manager when building the HttpClient; see https://hc.apache.org/httpcomponents-client-5.6.x/migration-guide/migration-to-classic.html and https://github.com/spring-projects/spring-framework/issues/35748 */
                  factory.setConnectTimeout(5000)
              }
              """
          )
        );
    }
}

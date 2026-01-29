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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class HttpComponentsClientHttpRequestFactoryConnectTimeoutTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HttpComponentsClientHttpRequestFactoryConnectTimeout())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-beans-5.3",
            "spring-boot-3",
            "spring-web-5.3",
            "httpclient5",
            "httpcore5"));
    }

    @DocumentExample
    @Test
    void addsCommentWhenNoPoolingHttpClientConnectionManager() {
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
                      // Manual migration to `ConnectionConfig.Builder.setConnectTimeout(Timeout)` necessary; see: https://github.com/spring-projects/spring-framework/issues/35748
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
                      // Manual migration to `ConnectionConfig.Builder.setConnectTimeout(Timeout)` necessary; see: https://github.com/spring-projects/spring-framework/issues/35748
                      factory.setConnectTimeout(5000);
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleSetterCalls() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

              class Example {
                  HttpComponentsClientHttpRequestFactory configure() {
                      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
                      factory.setConnectTimeout(5000);
                      factory.setConnectionRequestTimeout(3000);
                      return factory;
                  }
              }
              """,
            """
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

              class Example {
                  HttpComponentsClientHttpRequestFactory configure() {
                      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
                      // Manual migration to `ConnectionConfig.Builder.setConnectTimeout(Timeout)` necessary; see: https://github.com/spring-projects/spring-framework/issues/35748
                      factory.setConnectTimeout(5000);
                      factory.setConnectionRequestTimeout(3000);
                      return factory;
                  }
              }
              """
          )
        );
    }
}

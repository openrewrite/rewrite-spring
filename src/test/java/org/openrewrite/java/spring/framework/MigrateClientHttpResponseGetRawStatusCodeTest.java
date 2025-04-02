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

class MigrateClientHttpResponseGetRawStatusCodeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-web-6.1.+"));
    }

    @Test
    @DocumentExample
    void encodeDecode() {
        rewriteRun(
          //language=java
          java(
            """
              package test;

              import java.io.IOException;

              import org.springframework.http.HttpRequest;
              import org.springframework.http.client.ClientHttpRequestExecution;
              import org.springframework.http.client.ClientHttpRequestInterceptor;
              import org.springframework.http.client.ClientHttpResponse;

              class LoggingInterceptor implements ClientHttpRequestInterceptor {

                  @Override
                  public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
                      ClientHttpResponse response = execution.execute(request, body);
                      int rawStatusCode = response.getRawStatusCode();
                      return response;
                  }
              }
              """,
            """
              package test;

              import java.io.IOException;

              import org.springframework.http.HttpRequest;
              import org.springframework.http.client.ClientHttpRequestExecution;
              import org.springframework.http.client.ClientHttpRequestInterceptor;
              import org.springframework.http.client.ClientHttpResponse;

              class LoggingInterceptor implements ClientHttpRequestInterceptor {

                  @Override
                  public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
                      ClientHttpResponse response = execution.execute(request, body);
                      int rawStatusCode = response.getStatusCode();
                      return response;
                  }
              }
              """
          )
        );
    }

}

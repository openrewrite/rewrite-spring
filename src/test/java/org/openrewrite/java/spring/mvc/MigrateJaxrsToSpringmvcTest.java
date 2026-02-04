/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.mvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateJaxrsToSpringmvcTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.spring.mvc.MigrateJaxRsToSpringMvc"))
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "jakarta.ws.rs-api-4", "javax.ws.rs-api-2"));;
    }

    @DocumentExample
    @Test
    void jaxrsToSpringmvcJavaxTest() {
        rewriteRun(
          java(
              """
              import java.io.IOException;

              import javax.ws.rs.core.CacheControl;
              import javax.ws.rs.core.HttpHeaders;
              import javax.ws.rs.core.Request;
              import javax.ws.rs.core.StreamingOutput;

              public class Test {

                  public StreamingOutput test(Request request, HttpHeaders headers, CacheControl cc) {
                      return output -> {
                          try {
                              String message = "Request: " + request.getMethod() + "\\n" +
                                      "Headers: " + headers.toString() + "\\n" +
                                      "CacheControl: " + cc.toString();
                              output.write(message.getBytes());
                          } catch (IOException e) {
                              throw new RuntimeException(e);
                          }
                      };
                  }
              }
              """,
              """
              import java.io.IOException;

              import javax.servlet.http.HttpServletRequest;

              import org.springframework.http.CacheControl;
              import org.springframework.http.HttpHeaders;
              import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

              public class Test {

                  public StreamingResponseBody test(HttpServletRequest request, HttpHeaders headers, CacheControl cc) {
                      return output -> {
                          try {
                              String message = "Request: " + request.getMethod() + "\\n" +
                                      "Headers: " + headers.toString() + "\\n" +
                                      "CacheControl: " + cc.toString();
                              output.write(message.getBytes());
                          } catch (IOException e) {
                              throw new RuntimeException(e);
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void jaxrsToSpringmvcJakartaTest() {
        rewriteRun(
          java(
              """
              import java.io.IOException;

              import jakarta.ws.rs.core.CacheControl;
              import jakarta.ws.rs.core.HttpHeaders;
              import jakarta.ws.rs.core.Request;
              import jakarta.ws.rs.core.StreamingOutput;

              public class Test {

                  public StreamingOutput test(Request request, HttpHeaders headers, CacheControl cc) {
                      return output -> {
                          try {
                              String message = "Request: " + request.getMethod() + "\\n" +
                                      "Headers: " + headers.toString() + "\\n" +
                                      "CacheControl: " + cc.toString();
                              output.write(message.getBytes());
                          } catch (IOException e) {
                              throw new RuntimeException(e);
                          }
                      };
                  }
              }
              """,
              """
              import java.io.IOException;

              import jakarta.servlet.http.HttpServletRequest;
              import org.springframework.http.CacheControl;
              import org.springframework.http.HttpHeaders;
              import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

              public class Test {

                  public StreamingResponseBody test(HttpServletRequest request, HttpHeaders headers, CacheControl cc) {
                      return output -> {
                          try {
                              String message = "Request: " + request.getMethod() + "\\n" +
                                      "Headers: " + headers.toString() + "\\n" +
                                      "CacheControl: " + cc.toString();
                              output.write(message.getBytes());
                          } catch (IOException e) {
                              throw new RuntimeException(e);
                          }
                      };
                  }
              }
              """
          )
        );
    }

}

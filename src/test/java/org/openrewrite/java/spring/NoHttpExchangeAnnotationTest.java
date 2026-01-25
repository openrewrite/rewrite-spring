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
package org.openrewrite.java.spring;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class NoHttpExchangeAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoHttpExchangeAnnotation())
          .parser(JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.+"));
    }

    @DocumentExample
    @Test
    void getExchange() {
        rewriteRun(
          java(
            """
              import org.springframework.web.service.annotation.HttpExchange;

              interface UserApi {
                  @HttpExchange(method = "GET", value = "/api/users")
                  String getUsers();
              }
              """,
            """
              import org.springframework.web.service.annotation.GetExchange;

              interface UserApi {
                  @GetExchange("/api/users")
                  String getUsers();
              }
              """
          )
        );
    }

    @Test
    void postExchange() {
        rewriteRun(
          java(
            """
              import org.springframework.web.service.annotation.HttpExchange;

              interface UserApi {
                  @HttpExchange(method = "POST", value = "/api/users")
                  String postUser();
              }
              """,
            """
              import org.springframework.web.service.annotation.PostExchange;

              interface UserApi {
                  @PostExchange("/api/users")
                  String postUser();
              }
              """
          )
        );
    }

    @Test
    void putExchange() {
        rewriteRun(
          java(
            """
              import org.springframework.web.service.annotation.HttpExchange;

              interface UserApi {
                  @HttpExchange(method = "PUT", value = "/api/users/{id}")
                  String putUser();
              }
              """,
            """
              import org.springframework.web.service.annotation.PutExchange;

              interface UserApi {
                  @PutExchange("/api/users/{id}")
                  String putUser();
              }
              """
          )
        );
    }

    @Test
    void patchExchange() {
        rewriteRun(
          java(
            """
              import org.springframework.web.service.annotation.HttpExchange;

              interface UserApi {
                  @HttpExchange(method = "PATCH", value = "/api/users/{id}")
                  String patchUser();
              }
              """,
            """
              import org.springframework.web.service.annotation.PatchExchange;

              interface UserApi {
                  @PatchExchange("/api/users/{id}")
                  String patchUser();
              }
              """
          )
        );
    }

    @Test
    void deleteExchange() {
        rewriteRun(
          java(
            """
              import org.springframework.web.service.annotation.HttpExchange;

              interface UserApi {
                  @HttpExchange(method = "DELETE", value = "/api/users")
                  String deleteUser();
              }
              """,
            """
              import org.springframework.web.service.annotation.DeleteExchange;

              interface UserApi {
                  @DeleteExchange("/api/users")
                  String deleteUser();
              }
              """
          )
        );
    }

    @Test
    void methodOnlyWithoutValue() {
        rewriteRun(
          java(
            """
              import org.springframework.web.service.annotation.HttpExchange;

              interface UserApi {
                  @HttpExchange(method = "GET")
                  String getUsers();
              }
              """,
            """
              import org.springframework.web.service.annotation.GetExchange;

              interface UserApi {
                  @GetExchange
                  String getUsers();
              }
              """
          )
        );
    }

    @Test
    void noMethodArgument() {
        rewriteRun(
          java(
            """
              import org.springframework.web.service.annotation.HttpExchange;

              interface UserApi {
                  @HttpExchange("/api/users")
                  String getUsers();
              }
              """
          )
        );
    }

    @Test
    void multipleMethods() {
        rewriteRun(
          java(
            """
              import org.springframework.web.service.annotation.HttpExchange;

              interface UserApi {
                  @HttpExchange(method = "GET", value = "/api/users")
                  String getUsers();

                  @HttpExchange(method = "POST", value = "/api/users")
                  String createUser();

                  @HttpExchange(method = "DELETE", value = "/api/users/{id}")
                  void deleteUser();
              }
              """,
            """
              import org.springframework.web.service.annotation.DeleteExchange;
              import org.springframework.web.service.annotation.GetExchange;
              import org.springframework.web.service.annotation.PostExchange;

              interface UserApi {
                  @GetExchange("/api/users")
                  String getUsers();

                  @PostExchange("/api/users")
                  String createUser();

                  @DeleteExchange("/api/users/{id}")
                  void deleteUser();
              }
              """
          )
        );
    }
}

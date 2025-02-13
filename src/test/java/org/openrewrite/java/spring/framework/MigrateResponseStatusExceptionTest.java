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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateResponseStatusExceptionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.MigrateResponseStatusException")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-core-5.", "spring-beans-5.", "spring-web-5."));
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-spring/issues/554")
    @Test
    void migrateResponseStatusExceptionGetStatusMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpStatus;
              import org.springframework.web.server.ResponseStatusException;

              class A {
                  void foo(ResponseStatusException e) {
                      HttpStatus i = e.getStatus();
                  }
              }
              """,
            """
              import org.springframework.http.HttpStatusCode;
              import org.springframework.web.server.ResponseStatusException;

              class A {
                  void foo(ResponseStatusException e) {
                      HttpStatusCode i = e.getStatusCode();
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-spring/issues/554")
    @Test
    void migrateResponseStatusExceptionGetRawStatusCodeMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.server.ResponseStatusException;
              class A {
                  void foo(ResponseStatusException e) {
                      int i = e.getRawStatusCode();
                  }
              }
              """,
            """
              import org.springframework.web.server.ResponseStatusException;
              class A {
                  void foo(ResponseStatusException e) {
                      int i = e.getStatusCode().value();
                  }
              }
              """
          )
        );
    }

}

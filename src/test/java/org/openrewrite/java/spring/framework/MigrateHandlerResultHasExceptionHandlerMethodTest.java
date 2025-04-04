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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateHandlerResultHasExceptionHandlerMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .typeValidationOptions(TypeValidation.none())
          .recipe(new MigrateHandlerResultHasExceptionHandlerMethod())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "reactor-core-3.6.+", "spring-webflux-6.1.+"));
    }

    @DocumentExample
    @Test
    void migrateWithinIfStatement() {
        rewriteRun(
          // language=java
          java(
            """
              import org.springframework.web.reactive.HandlerResult;
              class MyHandler {
                  void configureHandler(HandlerResult result) {
                      if (result.hasExceptionHandler()) {
                          // do something
                      }
                  }
              }
              """,
            """
              import org.springframework.web.reactive.HandlerResult;
              class MyHandler {
                  void configureHandler(HandlerResult result) {
                      if (result.getExceptionHandler() != null) {
                          // do something
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateWithinVariableDeclaration() {
        rewriteRun(
          // language=java
          java(
            """
              import org.springframework.web.reactive.HandlerResult;
              class MyHandler {
                  void configureHandler(HandlerResult result) {
                      boolean b = result.hasExceptionHandler();
                  }
              }
              """,
            """
              import org.springframework.web.reactive.HandlerResult;
              class MyHandler {
                  void configureHandler(HandlerResult result) {
                      boolean b = result.getExceptionHandler() != null;
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateWithinMethodInvocation() {
        rewriteRun(
          // language=java
          java(
            """
              import org.springframework.web.reactive.HandlerResult;
              class MyHandler {
                  void configureHandler(HandlerResult result) {
                      set(result.hasExceptionHandler(), "foo");
                  }

                  void set(boolean b, String s) {
                      // do something
                  }
              }
              """,
            """
              import org.springframework.web.reactive.HandlerResult;
              class MyHandler {
                  void configureHandler(HandlerResult result) {
                      set(result.getExceptionHandler() != null, "foo");
                  }

                  void set(boolean b, String s) {
                      // do something
                  }
              }
              """
          )
        );
    }
}

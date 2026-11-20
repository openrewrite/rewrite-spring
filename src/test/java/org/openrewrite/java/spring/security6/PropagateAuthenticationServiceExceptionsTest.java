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
package org.openrewrite.java.spring.security6;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PropagateAuthenticationServiceExceptionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new PropagateAuthenticationServiceExceptions())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-context-5.3.+",
            "spring-beans-5.3.+",
            "spring-web-5.3.+",
            "spring-security-web-5.8.+",
            "spring-security-config-5.8.+"));
    }

    @DocumentExample
    @SuppressWarnings("DataFlowIssue")
    @Test
    void removeCallWithTrueLiteral() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

              class T {
                  void m() {
                      AuthenticationEntryPointFailureHandler handler = null;
                      handler.setRethrowAuthenticationServiceException(true);
                  }
              }
              """,
            """
              import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

              class T {
                  void m() {
                      AuthenticationEntryPointFailureHandler handler = null;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepEnclosingCallOfBlockLambda() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

              import java.util.function.Consumer;

              class T {
                  void m() {
                      register(h -> {
                          h.setRethrowAuthenticationServiceException(true);
                      });
                  }
                  void register(Consumer<AuthenticationEntryPointFailureHandler> customizer) {
                  }
              }
              """,
            """
              import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

              import java.util.function.Consumer;

              class T {
                  void m() {
                      register(h -> {
                      });
                  }
                  void register(Consumer<AuthenticationEntryPointFailureHandler> customizer) {
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyExpressionLambdaBody() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

              import java.util.function.Consumer;

              class T {
                  Consumer<AuthenticationEntryPointFailureHandler> c = h -> h.setRethrowAuthenticationServiceException(true);
              }
              """,
            """
              import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

              import java.util.function.Consumer;

              class T {
                  Consumer<AuthenticationEntryPointFailureHandler> c = h -> {};
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void keepFalseArgument() {
            // language=java
            rewriteRun(
              java(
                """
                  import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

                  class T {
                      void m(AuthenticationEntryPointFailureHandler handler) {
                          handler.setRethrowAuthenticationServiceException(false);
                      }
                  }
                  """
              )
            );
        }
    }
}

/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security5.UseNewRequestMatchers;
import org.openrewrite.java.spring.security6.PropagateAuthenticationServiceExceptions;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class PropagateAuthenticationServiceExceptionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PropagateAuthenticationServiceExceptions())
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .classpathFromResources(new InMemoryExecutionContext(),"spring-context-5.3.+", "spring-beans-5.3.+", "spring-web-5.3.+", "spring-security-web-5.8.+", "spring-security-config-5.8.+"));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
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
}

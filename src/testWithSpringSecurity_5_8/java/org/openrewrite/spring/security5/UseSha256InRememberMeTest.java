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
package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security6.UseSha256InRememberMe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseSha256InRememberMeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseSha256InRememberMe())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-beans-6.+",
              "spring-context-6.+",
              "spring-security-core-6.+",
              "spring-security-config-6.+",
              "spring-security-web-6.+"));
    }

    @DocumentExample
    @Test
    void removeExplicitEncodingAlgorithmOnly() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.core.userdetails.UserDetailsService;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;

              import static org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm.SHA256;

              class T {
                  void qualifiedFieldAccess(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService, RememberMeTokenAlgorithm.SHA256);
                  }
                  void staticImport(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService, SHA256);
                  }
                  void localVariable(UserDetailsService userDetailsService) {
                      final RememberMeTokenAlgorithm encodingAlgorithm = RememberMeTokenAlgorithm.SHA256;
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService, encodingAlgorithm);
                  }
                  void classField(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService, encodingAlgorithm);
                  }
                  void constantInOtherClass(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService, X.ALGORITHM);
                  }
                  final RememberMeTokenAlgorithm encodingAlgorithm = RememberMeTokenAlgorithm.SHA256;
              }
              class X {
                  static final RememberMeTokenAlgorithm ALGORITHM = RememberMeTokenAlgorithm.SHA256;
              }
              """,
            """
              import org.springframework.security.core.userdetails.UserDetailsService;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;

              import static org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm.SHA256;

              class T {
                  void qualifiedFieldAccess(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService);
                  }
                  void staticImport(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService);
                  }
                  void localVariable(UserDetailsService userDetailsService) {
                      final RememberMeTokenAlgorithm encodingAlgorithm = RememberMeTokenAlgorithm.SHA256;
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService);
                  }
                  void classField(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService);
                  }
                  void constantInOtherClass(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService, X.ALGORITHM);
                  }
                  final RememberMeTokenAlgorithm encodingAlgorithm = RememberMeTokenAlgorithm.SHA256;
              }
              class X {
                  static final RememberMeTokenAlgorithm ALGORITHM = RememberMeTokenAlgorithm.SHA256;
              }
              """
          )
        );
    }

    @Test
    void removeExplicitMatchingAlgorithmOnly() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.core.userdetails.UserDetailsService;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;

              class T {
                  void qualifiedFieldAccess(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService);
                      rememberMe.setMatchingAlgorithm(RememberMeTokenAlgorithm.SHA256);
                  }
              }
              """,
            """
              import org.springframework.security.core.userdetails.UserDetailsService;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;

              class T {
                  void qualifiedFieldAccess(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeBoth() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.core.userdetails.UserDetailsService;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;

              class T {
                  void qualifiedFieldAccess(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService, RememberMeTokenAlgorithm.SHA256);
                      rememberMe.setMatchingAlgorithm(RememberMeTokenAlgorithm.SHA256);
                  }
              }
              """,
            """
              import org.springframework.security.core.userdetails.UserDetailsService;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
              import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;

              class T {
                  void qualifiedFieldAccess(UserDetailsService userDetailsService) {
                      TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("key", userDetailsService);
                  }
              }
              """
          )
        );
    }
}

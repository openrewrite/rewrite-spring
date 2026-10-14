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
package org.openrewrite.java.spring.security7;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SecurityConfigurerRemoveThrowsExceptionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.security7.SecurityConfigurerRemoveThrowsException")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-security-core-6",
              "spring-security-config-6.2",
              "spring-security-web-6.2",
              "spring-context-6"));
    }

    @DocumentExample
    @Test
    void removesThrowsFromConfigurerOverrides() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.DefaultSecurityFilterChain;

              class MyConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {
                  @Override
                  public void init(HttpSecurity builder) throws Exception {
                  }

                  @Override
                  public void configure(HttpSecurity builder) throws Exception {
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.DefaultSecurityFilterChain;

              class MyConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {
                  @Override
                  public void init(HttpSecurity builder) {
                  }

                  @Override
                  public void configure(HttpSecurity builder) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesThrowsWhenCheckedExceptionIsCaught() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.DefaultSecurityFilterChain;

              import java.io.IOException;
              import java.nio.file.Files;
              import java.nio.file.Path;

              class MyConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {
                  @Override
                  public void configure(HttpSecurity builder) throws Exception {
                      try {
                          Files.readAllBytes(Path.of("keys.pem"));
                      } catch (IOException e) {
                          throw new IllegalStateException(e);
                      }
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.DefaultSecurityFilterChain;

              import java.io.IOException;
              import java.nio.file.Files;
              import java.nio.file.Path;

              class MyConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {
                  @Override
                  public void configure(HttpSecurity builder) {
                      try {
                          Files.readAllBytes(Path.of("keys.pem"));
                      } catch (IOException e) {
                          throw new IllegalStateException(e);
                      }
                  }
              }
              """
          )
        );
    }
}

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
package org.openrewrite.java.spring.security6;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.RemoveMethodInvocationsVisitor;
import org.openrewrite.test.AdHocRecipe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.java.Assertions.java;

class RequireExplicitSavingOfSecurityContextRepositoryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        Recipe recipe = new AdHocRecipe(null, null, null,
          () -> new RemoveMethodInvocationsVisitor(List.of("org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer requireExplicitSave(boolean)")),
          null, null, null, emptyList());

        spec.recipe(recipe)
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),"spring-security-config-5.8.+", "spring-security-web-5.8.+"));
    }

    @Test
    void firstInChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(true)
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void middleOfChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                              .requireExplicitSave(true)
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void lastInChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                              .requireExplicitSave(true)
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyInChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(true)
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("CodeBlock2Expr")
    void onlyInChainWithBlockBody() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> {
                              securityContext.requireExplicitSave(true);
                          });
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void withLeadingOuterChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                          )
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(true)
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http
                          .securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void unrelatedEmptyLambda() {
        // language=java
        rewriteRun(
          java(
            """
              class T {
                  public void m() {
                      java.util.function.Consumer<Object> consumer = (o) -> {};
                 }
              }
              """
          )
        );
    }
}
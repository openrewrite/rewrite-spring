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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.spring.security5.UseNewRequestMatchers;
import org.openrewrite.java.spring.security5.UseNewSecurityMatchers;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class UseNewSecurityMatchersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseNewSecurityMatchers())
          .parser(JavaParser.fromJavaVersion().classpath(
            "spring-context-5.3.+", "spring-beans-5.3.+", "spring-web-5.3.+", "spring-security-web-5.7.+", "spring-security-config-5.7.+"));
    }

    @DocumentExample
    @Test
    void migrateAntMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) {
                      http
                              .antMatcher("/static/**")
                              .authorizeHttpRequests((authz) -> authz
                                      .mvcMatchers("/static/**").permitAll()
                              );
                      return http.build();
                  }
              }
              """
            ,
            """
              package com.example;

              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) {
                      http
                              .securityMatcher("/static/**")
                              .authorizeHttpRequests((authz) -> authz
                                      .mvcMatchers("/static/**").permitAll()
                              );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedCalls() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.demo;

              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) {
                      http.antMatcher("/**").authorizeRequests()
                              .antMatchers(HttpMethod.POST, "/verify").access("hasRole('ROLE_USER')")
                              .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """, """
              package com.example.demo;

              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) {
                      http.securityMatcher("/**").authorizeRequests()
                              .antMatchers(HttpMethod.POST, "/verify").access("hasRole('ROLE_USER')")
                              .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """)
        );
    }

    @Test
    void togetherWithRequestMatchers() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(toRecipe(() -> new JavaVisitor<>() {
              @Override
              public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                  tree = new UseNewRequestMatchers().getVisitor().visit(tree, ctx);
                  tree = new UseNewSecurityMatchers().getVisitor().visit(tree, ctx);
                  return (J) tree;
              }
          })),
          java(
            """
              package com.example;

              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) {
                      http
                              .antMatcher("/static/**")
                              .authorizeHttpRequests((authz) -> authz
                                      .mvcMatchers("/static/**").permitAll()
                              );
                      return http.build();
                  }
              }
              """
            ,
            """
              package com.example;

              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) {
                      http
                              .securityMatcher("/static/**")
                              .authorizeHttpRequests((authz) -> authz
                                      .requestMatchers("/static/**").permitAll()
                              );
                      return http.build();
                  }
              }
              """
          )
        );
    }

}

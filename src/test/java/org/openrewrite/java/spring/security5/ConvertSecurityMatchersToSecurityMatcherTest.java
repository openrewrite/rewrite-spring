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
package org.openrewrite.java.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConvertSecurityMatchersToSecurityMatcherTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertSecurityMatchersToSecurityMatcher())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-context-5.3.+", "spring-beans-5.3.+", "spring-web-5.3.+",
            "spring-security-web-5.8.+", "spring-security-config-5.8.+"));
    }

    @DocumentExample
    @Test
    void requestMatchersAntMatchersToSecurityMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http.requestMatchers().antMatchers("/oidc/*", "/events/syncs**");
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http.securityMatcher("/oidc/*", "/events/syncs**");
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void requestMatchersAntMatchersWithAndChain() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .requestMatchers()
                                  .antMatchers("/oidc/*", "/events/syncs**")
                                  .and()
                              .authorizeRequests()
                                  .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .securityMatcher("/oidc/*", "/events/syncs**")
                              .authorizeRequests()
                                  .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void requestMatchersMvcMatchersToSecurityMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .requestMatchers()
                                  .mvcMatchers("/api/**")
                                  .and()
                              .authorizeRequests()
                                  .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .securityMatcher("/api/**")
                              .authorizeRequests()
                                  .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void requestMatchersRegexMatchersToSecurityMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .requestMatchers()
                                  .regexMatchers("/api/.*")
                                  .and()
                              .authorizeRequests()
                                  .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .securityMatcher("/api/.*")
                              .authorizeRequests()
                                  .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenAntMatchersInsideAuthorizeRequests() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeRequests()
                                  .antMatchers("/public/**").permitAll()
                                  .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenRequestMatchersHasLambdaArg() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              class SecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .requestMatchers(matchers -> matchers
                                  .antMatchers("/api/**"))
                              .authorizeRequests()
                                  .anyRequest().authenticated();
                      return http.build();
                  }
              }
              """
          )
        );
    }
}

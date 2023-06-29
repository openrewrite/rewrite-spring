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
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security6.RemoveOauth2LoginConfig;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class RemoveOauth2LoginConfigTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveOauth2LoginConfig())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),"spring-context-5.3.+", "spring-beans-5.3.+", "spring-web-5.3.+", "spring-security-web-5.8.+", "spring-security-config-5.8.+"));
    }

    @DocumentExample
    @Test
    void removeUnneededConfigFromEndOfCallChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              import java.util.HashSet;
              import java.util.Set;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login()
                          .userInfoEndpoint()
                              .userAuthoritiesMapper(null);
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

              import java.util.HashSet;
              import java.util.Set;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated());
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeIfMethodsAreInTheMiddleOfChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              import java.util.HashSet;
              import java.util.Set;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login()
                          .userInfoEndpoint()
                              .oidcUserService(null);
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUserInfoEndpointStatement() {
        // language=java
        rewriteRun(
          // spec -> spec.cycles(2).expectedCyclesThatMakeChanges(2),
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      OAuth2LoginConfigurer<HttpSecurity> auth2 = http.oauth2Login();
                      auth2.userInfoEndpoint()
                          .userAuthoritiesMapper(null);
                      auth2.init(null);
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      OAuth2LoginConfigurer<HttpSecurity> auth2 = http.oauth2Login();
                      auth2.init(null);
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUserAuthoritiesMapperStatement() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      OAuth2LoginConfigurer<HttpSecurity>.UserInfoEndpointConfig x = http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login()
                          .userInfoEndpoint();
                      x.userAuthoritiesMapper(null);
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      OAuth2LoginConfigurer<HttpSecurity>.UserInfoEndpointConfig x = http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login()
                          .userInfoEndpoint();
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForReturn() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;

              public class config2 {
                  OAuth2LoginConfigurer<HttpSecurity> get(HttpSecurity http) throws Exception {
                      return http.oauth2Login();
                  }
              }
              """
          )
        );
    }
}

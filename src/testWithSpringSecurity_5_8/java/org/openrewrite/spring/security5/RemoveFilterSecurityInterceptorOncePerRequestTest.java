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
import org.openrewrite.java.spring.security6.RemoveFilterSecurityInterceptorOncePerRequest;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class RemoveFilterSecurityInterceptorOncePerRequestTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveFilterSecurityInterceptorOncePerRequest())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "spring-context-5.3.+", "spring-beans-5.3.+", "spring-web-5.3.+", "spring-security-web-5.8.+", "spring-security-config-5.8.+"));
    }

    @DocumentExample
    @Test
    void removeFilterSecurityInterceptorOncePerRequestWithFalse() {
        rewriteRun(
          java(
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  SecurityFilterChain web(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests((authorize) -> authorize
                              .filterSecurityInterceptorOncePerRequest(false)
                              .requestMatchers(HttpMethod.GET, "/static/**")
                              .permitAll()
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  SecurityFilterChain web(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests((authorize) -> authorize
                              .requestMatchers(HttpMethod.GET, "/static/**")
                              .permitAll()
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveFilterSecurityInterceptorOncePerRequestWithTrue() {
        rewriteRun(
          java(
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  SecurityFilterChain web(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests((authorize) -> authorize
                              .filterSecurityInterceptorOncePerRequest(true)
                              .requestMatchers(HttpMethod.GET, "/static/**")
                              .permitAll()
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }
}

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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ServerHttpSecurityLambdaDslTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ServerHttpSecurityLambdaDsl())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-beans", "spring-context", "spring-boot", "spring-security", "spring-web", "tomcat-embed", "spring-core"));
    }

    @DocumentExample
    @Test
    void simple() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
              import org.springframework.security.config.web.server.ServerHttpSecurity;
              import org.springframework.security.web.server.SecurityWebFilterChain;

              @EnableWebFluxSecurity
              public class SecurityConfig {
                  @Bean
                  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
                      http.authorizeExchange()
                              .pathMatchers("/blog/**").permitAll()
                              .anyExchange().authenticated();
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
              import org.springframework.security.config.web.server.ServerHttpSecurity;
              import org.springframework.security.web.server.SecurityWebFilterChain;

              @EnableWebFluxSecurity
              public class SecurityConfig {
                  @Bean
                  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
                      http.authorizeExchange(exchange -> exchange
                              .pathMatchers("/blog/**").permitAll()
                              .anyExchange().authenticated());
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void advanced() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
              import org.springframework.security.config.web.server.ServerHttpSecurity;
              import org.springframework.security.web.server.SecurityWebFilterChain;

              @EnableWebFluxSecurity
              public class SecurityConfig {
                  @Bean
                  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
                      http
                              .authorizeExchange()
                                      .pathMatchers("/blog/**").permitAll()
                                      .anyExchange().authenticated()
                                      .and()
                              .httpBasic()
                                      .and()
                              .formLogin()
                                      .loginPage("/login");
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
              import org.springframework.security.config.web.server.ServerHttpSecurity;
              import org.springframework.security.web.server.SecurityWebFilterChain;

              import static org.springframework.security.config.Customizer.withDefaults;

              @EnableWebFluxSecurity
              public class SecurityConfig {
                  @Bean
                  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
                      http
                              .authorizeExchange(exchange -> exchange
                                      .pathMatchers("/blog/**").permitAll()
                                      .anyExchange().authenticated())
                              .httpBasic(withDefaults())
                              .formLogin(login -> login
                                      .loginPage("/login"));
                      return http.build();
                  }
              }
              """
          )
        );
    }

}

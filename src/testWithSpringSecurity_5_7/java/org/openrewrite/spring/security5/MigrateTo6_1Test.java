/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateTo6_1Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_1")
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-security", "spring-web", "tomcat-embed", "spring-context", "spring-beans"));
    }

    @Test
    void test_1() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.SecurityFilterChain;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests()
                          .antMatchers(HttpMethod.OPTIONS, "/rest/**").permitAll()
                          .anyRequest().permitAll()
                          .and().csrf().disable();
                      return http.build();
                  }
              }
              """, """
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.SecurityFilterChain;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests(requests -> requests
                                      .requestMatchers(HttpMethod.OPTIONS, "/rest/**").permitAll()
                                      .anyRequest().permitAll()).csrf(csrf -> csrf.disable());
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void test_2() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.SecurityFilterChain;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests()
                          .antMatchers(HttpMethod.OPTIONS, "/rest/**").permitAll()
                          .antMatchers("/openapi").permitAll()
                          .antMatchers("/openapi.yaml").permitAll()
                          .antMatchers("/openapi/**").permitAll()
                          .antMatchers("/rest/hello").permitAll()
                          .antMatchers("/actuator/**").permitAll()
                          .anyRequest().authenticated()
                          .and().oauth2ResourceServer().jwt();
                      return http.build();
                  }
              }
              """, """
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.SecurityFilterChain;
              
              import static org.springframework.security.config.Customizer.withDefaults;
              
              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests(requests -> requests
                                      .requestMatchers(HttpMethod.OPTIONS, "/rest/**").permitAll()
                                      .requestMatchers("/openapi").permitAll()
                                      .requestMatchers("/openapi.yaml").permitAll()
                                      .requestMatchers("/openapi/**").permitAll()
                                      .requestMatchers("/rest/hello").permitAll()
                                      .requestMatchers("/actuator/**").permitAll()
                                      .anyRequest().authenticated()).oauth2ResourceServer(server -> server.jwt(withDefaults()));
                      return http.build();
                  }
              }
              """
          )
        );
    }

}

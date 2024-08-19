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
import org.openrewrite.DocumentExample;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.RecipesThatMadeChanges;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.Collection;
import java.util.List;

import static org.openrewrite.java.Assertions.java;

class UpgradeSpringSecurity58Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.security5.UpgradeSpringSecurity_5_8")
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-security", "spring-web", "tomcat-embed", "spring-context", "spring-beans"));
    }

    @DocumentExample
    @Test
    void shouldRetainAntMatchers() {
        //language=java
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2) // TODO Ideally we would expect 1 cycle
            .afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).build()), // TODO Remove suppression
          java(
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.SecurityFilterChain;

              @EnableWebSecurity
              class SecurityConfig extends WebSecurityConfigurerAdapter {
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
              """,
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.SecurityFilterChain;

              @EnableWebSecurity
              class SecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests(requests -> requests
                                      .requestMatchers(HttpMethod.OPTIONS, "/rest/**").permitAll()
                                      .anyRequest().permitAll()).csrf(csrf -> csrf.disable());
                      return http.build();
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> {
                RecipesThatMadeChanges recipesThatMadeChanges = cu.getMarkers().findFirst(RecipesThatMadeChanges.class).get();
                Collection<List<Recipe>> recipes = recipesThatMadeChanges.getRecipes();
                for (List<Recipe> recipeList : recipes) {
                    for (Recipe recipe : recipeList) {
                        System.out.printf(recipe.getName() + "\n");
                    }
                    System.out.printf("\n");
                }
            })
          )
        );
    }

    @Test
    void shouldRetainChainOfAntMatchers() {
        //language=java
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
          java(
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.SecurityFilterChain;

              @EnableWebSecurity
              class SecurityConfig extends WebSecurityConfigurerAdapter {
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
              """,
            """
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.SecurityFilterChain;

              @EnableWebSecurity
              class SecurityConfig extends WebSecurityConfigurerAdapter {
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
                                      .anyRequest().authenticated()).oauth2ResourceServer(server -> server.jwt());
                      return http.build();
                  }
              }
             """
          )
        );
    }

}

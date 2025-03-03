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
package org.openrewrite.java.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AuthorizeHttpRequestsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AuthorizeHttpRequests())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-beans", "spring-context", "spring-boot", "spring-security", "spring-web", "tomcat-embed", "spring-core"));
    }

    @DocumentExample
    @Test
    void noArgAuthorizeRequests() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests()
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated()
                              .and()
                          .formLogin()
                              .loginPage("/login")
                              .permitAll()
                              .and()
                          .rememberMe();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests()
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated()
                              .and()
                          .formLogin()
                              .loginPage("/login")
                              .permitAll()
                              .and()
                          .rememberMe();
                  }
              }
              """
          )
        );
    }

    @Test
    void noArgAuthorizeRequestsWithVars() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              public class JdbcSecurityConfiguration {
                  @Bean
                  SecurityFilterChain web(HttpSecurity http) throws Exception {
                      ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry reqs = http.authorizeRequests();
                      reqs.antMatchers("/ll").authenticated();
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              public class JdbcSecurityConfiguration {
                  @Bean
                  SecurityFilterChain web(HttpSecurity http) throws Exception {
                      AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry reqs = http.authorizeHttpRequests();
                      reqs.antMatchers("/ll").authenticated();
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdaAuthorizeRequests() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              import static org.springframework.security.config.Customizer.withDefaults;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests(authorizeRequests ->
                              authorizeRequests
                                  .antMatchers("/blog/**").permitAll()
                                  .anyRequest().authenticated()
                          )
                          .formLogin(formLogin ->
                              formLogin
                                  .loginPage("/login")
                                  .permitAll()
                          )
                          .rememberMe(withDefaults());
                  }

              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              import static org.springframework.security.config.Customizer.withDefaults;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests(authorizeRequests ->
                              authorizeRequests
                                  .antMatchers("/blog/**").permitAll()
                                  .anyRequest().authenticated()
                          )
                          .formLogin(formLogin ->
                              formLogin
                                  .loginPage("/login")
                                  .permitAll()
                          )
                          .rememberMe(withDefaults());
                  }

              }
              """
          )
        );
    }

    @Test
    void accessDecisionManagerTopInvocation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests()
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated()
                              // hello
                              .accessDecisionManager(null);
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      /*TODO: replace removed '.accessDecisionManager(null);' with appropriate call to 'access(AuthorizationManager)' after antMatcher(...) call etc.*/
                      http
                          .authorizeHttpRequests()
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated();
                  }
              }
              """
          )
        );
    }

    @Test
    void accessDecisionManager() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests()
                              .accessDecisionManager(null)
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class SecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests()
                              /*TODO: replace removed '.accessDecisionManager(null);' with appropriate call to 'access(AuthorizationManager)' after antMatcher(...) call etc.*/
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated();
                  }
              }
              """
          )
        );
    }

}

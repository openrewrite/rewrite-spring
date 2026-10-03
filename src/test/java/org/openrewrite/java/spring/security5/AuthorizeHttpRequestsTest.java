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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AuthorizeHttpRequestsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AuthorizeHttpRequests())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-2.7",
              "spring-beans-4",
              "spring-context-4",
              "spring-web-4",
              "spring-core-4",
              "spring-security-core-5.7",
              "spring-security-config-5.7",
              "spring-security-web-5.7",
              "tomcat-embed"));
    }

    @Test
    void noArgAuthorizeRequestsOnSpringSecurity51() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-beans-4", "spring-context-4", "spring-web-4", "spring-core-4",
              "spring-security-core-5.1", "spring-security-config-5.1", "spring-security-web-5.1")),
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;

              class Config {
                  void configure(HttpSecurity http) throws Exception {
                      http.authorizeRequests().anyRequest().authenticated();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;

              class Config {
                  void configure(HttpSecurity http) throws Exception {
                      http.authorizeHttpRequests().anyRequest().authenticated();
                  }
              }
              """
          )
        );
    }

    @Test
    void customizerWhenOriginalApiDoesNotDeclareReplacement() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
            """
              package org.springframework.security.config;
              public interface Customizer<T> { void customize(T target); }
              """,
            """
              package org.springframework.security.config.annotation.web.configurers;
              public class ExpressionUrlAuthorizationConfigurer<H> {
                  public class ExpressionInterceptUrlRegistry {
                      public ExpressionInterceptUrlRegistry anyRequest() { return this; }
                      public ExpressionInterceptUrlRegistry authenticated() { return this; }
                  }
              }
              """,
            """
              package org.springframework.security.config.annotation.web.builders;
              import org.springframework.security.config.Customizer;
              import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
              public class HttpSecurity {
                  public HttpSecurity authorizeRequests(Customizer<ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry> customizer) throws Exception {
                      return this;
                  }
              }
              """
          )),
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;

              class Config {
                  void configure(HttpSecurity http) throws Exception {
                      http.authorizeRequests(auth -> auth.anyRequest().authenticated());
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;

              class Config {
                  void configure(HttpSecurity http) throws Exception {
                      http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
                  }
              }
              """
          )
        );
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
                      AuthorizationManagerRequestMatcherRegistry reqs = http.authorizeHttpRequests();
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

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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class HeadersConfigurerLambdaDslTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HeadersConfigurerLambdaDsl())
          .parser(JavaParser.fromJavaVersion()
            .classpath(
              "spring-beans",
              "spring-context",
              "spring-boot",
              "spring-security-config-5.8.+",
              "spring-security-web-5.8.+",
              "spring-web",
              "tomcat-embed",
              "spring-core"
            ));
    }

    @DocumentExample
    @Test
    void simpleContentSecurityPolicy() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers(headers -> headers
                                       .contentSecurityPolicy("foobar"));
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers(headers -> headers
                                      .contentSecurityPolicy(policy -> policy
                                              .policyDirectives("foobar")));
                  }
              }
              """
          )
        );
    }

    @Test
    void xssProtectionEnable() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;

              @Configuration
              public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                        .headers()
                        .xssProtection().xssProtectionEnabled(true);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;

              @Configuration
              public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers()
                              .xssProtection(protection -> protection.and());
                  }
              }
              """
          )
        );
    }

    @Test
    void xssProtectionDisable() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;

              @Configuration
              public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                        .headers()
                        .xssProtection().xssProtectionEnabled(false);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;

              @Configuration
              public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers()
                              .xssProtection(protection -> protection.disable());
                  }
              }
              """
          )
        );
    }

    @Test
    void xssProtectionHeaderValue() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

              @Configuration
              public class WebSecurityConfig {
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .headers()
                          .xssProtection()
                          .xssProtectionEnabled(true)
                          .headerValue(XXssProtectionHeaderWriter.HeaderValue.DISABLED);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

              @Configuration
              public class WebSecurityConfig {
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers()
                              .xssProtection(protection -> protection
                                      .and()
                                      .headerValue(XXssProtectionHeaderWriter.HeaderValue.DISABLED));
                  }
              }
              """
          )
        );
    }

    @Test
    void complexContentSecurityPolicy() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers(headers -> headers
                                      .contentSecurityPolicy("foobar").reportOnly());
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers(headers -> headers
                                      .contentSecurityPolicy(policy -> policy
                                              .policyDirectives("foobar").reportOnly()));
                  }
              }
              """
          )
        );
    }

    @Test
    void referrerPolicy() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers(headers -> headers
                                       .referrerPolicy(ReferrerPolicy.ORIGIN));
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers(headers -> headers
                                      .referrerPolicy(policy -> policy
                                              .policy(ReferrerPolicy.ORIGIN)));
                  }
              }
              """
          )
        );
    }

    @Test
    void mix() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers(headers -> headers
                                       .contentSecurityPolicy("foobar").reportOnly().and()
                                       .cacheControl().and()
                                       .referrerPolicy(ReferrerPolicy.ORIGIN));
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

              import static org.springframework.security.config.Customizer.withDefaults;

              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {

                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .headers(headers -> headers
                                      .contentSecurityPolicy(policy -> policy
                                              .policyDirectives("foobar").reportOnly())
                                      .cacheControl(withDefaults())
                                      .referrerPolicy(policy -> policy
                                              .policy(ReferrerPolicy.ORIGIN)));
                  }
              }
              """
          )
        );
    }

}

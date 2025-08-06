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

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeSpringSecurityTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath(
                "spring-core-5.3.+", "spring-context-5.3.+", "spring-beans-5.3.+", "spring-web-5.3.+", "spring-security-web-5.8.+", "spring-security-config-5.8.+", "spring-security-core-5.8.+", "tomcat-embed"))
          .recipeFromResources("org.openrewrite.java.spring.security5.UpgradeSpringSecurity_5_8");
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-spring/pull/757")
    void canBeUpgradedIfAuthenticationManagerBuilderConfigurationPresent() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.http.SessionCreationPolicy;

              /**
               * Security configuration.
               */
              @Configuration
              @EnableWebSecurity
              public class  WebSecurityConfig extends WebSecurityConfigurerAdapter {

                  @Configuration
                  public static class AdminWebSecurityConfig extends WebSecurityConfigurerAdapter {

                      @Override
                      protected void configure( final AuthenticationManagerBuilder auth ) throws Exception {}

                      @Override
                      protected void configure( final HttpSecurity http ) throws Exception {
                          http.antMatcher( "***" )
                              .authorizeRequests().anyRequest().hasAuthority( "***" )
                              .and()
                              .sessionManagement().sessionCreationPolicy( SessionCreationPolicy.STATELESS )
                              .and().httpBasic().authenticationEntryPoint( null )
                              .and().csrf().disable();
                      }
                  }
              }
              """,
            """
            package com.example;

            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.http.SessionCreationPolicy;

            /**
             * Security configuration.
             */
            @Configuration
            @EnableWebSecurity
            public class  WebSecurityConfig {

                @Configuration
                public static class AdminWebSecurityConfig extends WebSecurityConfigurerAdapter {

                    /*~~(Migrate manually based on https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter)~~>*/@Override
                    protected void configure( final AuthenticationManagerBuilder auth ) throws Exception {}

                    @Override
                    protected void configure( final HttpSecurity http ) throws Exception {
                        http.securityMatcher("***")
                                .authorizeHttpRequests(requests -> requests.anyRequest().hasAuthority("***"))
                                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).httpBasic(basic -> basic.authenticationEntryPoint(null)).csrf(csrf -> csrf.disable());
                    }
                }
            }
            """
          )
        );
    }

}

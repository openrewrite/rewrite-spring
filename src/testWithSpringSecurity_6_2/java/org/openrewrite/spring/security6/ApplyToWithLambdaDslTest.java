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
package org.openrewrite.spring.security6;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security6.ApplyToWithLambdaDsl;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ApplyToWithLambdaDslTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ApplyToWithLambdaDsl())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-security-config", "spring-security-web"));
    }

    @Test
    void someConfigAndBuild() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
              import org.springframework.security.web.SecurityFilterChain;
              
              public class MySecurityConfig {
                  public SecurityFilterChain configure(HttpSecurity http) {
                      return http
                              .apply(new SessionManagementConfigurer<>())
                              .invalidSessionUrl("junk").and()
                              .build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
              import org.springframework.security.web.SecurityFilterChain;
              
              public class MySecurityConfig {
                  public SecurityFilterChain configure(HttpSecurity http) {
                      return http
                              .with(new SessionManagementConfigurer<>(), configurer -> configurer
                                      .invalidSessionUrl("junk"))
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void noAdditionalConfig() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
              import org.springframework.security.web.SecurityFilterChain;
              
              public class MySecurityConfig {
                  public SecurityFilterChain configure(HttpSecurity http) {
                      return http
                              .apply(new SessionManagementConfigurer<>())
                              .and()
                              .build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
              import org.springframework.security.web.SecurityFilterChain;
              
              import static org.springframework.security.config.Customizer.withDefaults;
              
              public class MySecurityConfig {
                  public SecurityFilterChain configure(HttpSecurity http) {
                      return http
                              .with(new SessionManagementConfigurer<>(), withDefaults())
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void standaloneApply() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
              import org.springframework.security.web.SecurityFilterChain;
              
              public class MySecurityConfig {
                  public SecurityFilterChain configure(HttpSecurity http) {
                      http.apply(new SessionManagementConfigurer<>());
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
              import org.springframework.security.web.SecurityFilterChain;
              
              import static org.springframework.security.config.Customizer.withDefaults;
              
              public class MySecurityConfig {
                  public SecurityFilterChain configure(HttpSecurity http) {
                      http.with(new SessionManagementConfigurer<>(), withDefaults());
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void withOtherConfigAfter() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
              import org.springframework.security.web.SecurityFilterChain;
              
              public class MySecurityConfig {
                  public SecurityFilterChain configure(HttpSecurity http) {
                      return http
                              .apply(new SessionManagementConfigurer<>())
                              .invalidSessionUrl("junk").and()
                              .csrf(csrf -> csrf.disable())
                              .build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
              import org.springframework.security.web.SecurityFilterChain;
              
              public class MySecurityConfig {
                  public SecurityFilterChain configure(HttpSecurity http) {
                      return http
                              .with(new SessionManagementConfigurer<>(), configurer -> configurer
                                      .invalidSessionUrl("junk"))
                              .csrf(csrf -> csrf.disable())
                              .build();
                  }
              }
              """
          )
        );
    }
}

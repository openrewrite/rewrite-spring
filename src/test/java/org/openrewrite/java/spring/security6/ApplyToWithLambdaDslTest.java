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
package org.openrewrite.java.spring.security6;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ApplyToWithLambdaDslTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ApplyToWithLambdaDsl())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "spring-security-config", "spring-security-web", "spring-context", "slf4j-api"));
    }

    @DocumentExample
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

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/468")
    @Test
    void customDslWithStaticFactoryMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
              import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              import static org.springframework.security.config.Customizer.withDefaults;

              public class MyCustomDsl extends AbstractHttpConfigurer<MyCustomDsl, HttpSecurity> {
                  private static final Logger log = LoggerFactory.getLogger(MyCustomDsl.class);

                  private boolean flag;

                  @Override
                  public void init(HttpSecurity http) throws Exception {
                      // do nothing
                      log.info("Entering MyCustomDsl.init");
                  }

                  @Override
                  public void configure(HttpSecurity http) throws Exception {
                      // do nothing
                      log.info("Entering MyCustomDsl.configure");
                  }

                  public MyCustomDsl flag(boolean value) {
                      this.flag = value;
                      return this;
                  }

                  public boolean isFlag() {
                      return flag;
                  }

                  public static MyCustomDsl customDsl() {
                      return new MyCustomDsl();
                  }

              }

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {

                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) {
                      http
                              .apply(MyCustomDsl.customDsl())
                              .and()
                              .formLogin(withDefaults());
                      return http.build();
                  }

              }
              """,
                """
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
                import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
                import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
                import org.springframework.security.web.SecurityFilterChain;

                import static org.springframework.security.config.Customizer.withDefaults;

                public class MyCustomDsl extends AbstractHttpConfigurer<MyCustomDsl, HttpSecurity> {
                    private static final Logger log = LoggerFactory.getLogger(MyCustomDsl.class);

                    private boolean flag;

                    @Override
                    public void init(HttpSecurity http) throws Exception {
                        // do nothing
                        log.info("Entering MyCustomDsl.init");
                    }

                    @Override
                    public void configure(HttpSecurity http) throws Exception {
                        // do nothing
                        log.info("Entering MyCustomDsl.configure");
                    }

                    public MyCustomDsl flag(boolean value) {
                        this.flag = value;
                        return this;
                    }

                    public boolean isFlag() {
                        return flag;
                    }

                    public static MyCustomDsl customDsl() {
                        return new MyCustomDsl();
                    }

                }

                @Configuration
                @EnableWebSecurity
                public class SecurityConfig {

                    @Bean
                    public SecurityFilterChain filterChain(HttpSecurity http) {
                        http
                                .with(MyCustomDsl.customDsl(), withDefaults())
                                .formLogin(withDefaults());
                        return http.build();
                    }

                }
                """
          )
        );
    }
}

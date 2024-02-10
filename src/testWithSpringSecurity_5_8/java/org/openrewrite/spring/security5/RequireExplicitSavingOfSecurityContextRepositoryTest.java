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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security6.RequireExplicitSavingOfSecurityContextRepository;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RequireExplicitSavingOfSecurityContextRepositoryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RequireExplicitSavingOfSecurityContextRepository())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "spring-security-config-5.8.+", "spring-security-web-5.8.+"));
    }

    @DocumentExample
    @Test
    void firstInChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http.securityContext((securityContext) -> securityContext
                              .requireExplicitSave(true)
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http.securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void middleOfChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http.securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                              .requireExplicitSave(true)
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http.securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void lastInChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http.securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                              .requireExplicitSave(true)
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http.securityContext((securityContext) -> securityContext
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyInChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) throws Exception {
                      http.securityContext(securityContext -> securityContext
                              .requireExplicitSave(true)
                              // some comments
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) throws Exception {
                      return http.build();
                  }
              }
              """
          )
        );
    }

    void consumer() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;

              import java.util.function.Consumer;
              import java.util.function.Function;

              public class config2 {
                  public void doSomething(
                      Consumer<SecurityContextConfigurer<HttpSecurity>> f1) {
                  }

                  void method() throws Exception {
                      doSomething(configurer -> {
                              configurer.requireExplicitSave(true);
                          }
                      );
                  }
              }
              """,
            """
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;

            import java.util.function.Consumer;
            import java.util.function.Function;

            public class config2 {
                public void doSomething(
                    Consumer<SecurityContextConfigurer<HttpSecurity>> f1) {
                }

                void method() throws Exception {
                    doSomething(configurer -> {
                        }
                    );
                }
            }
            """
          )
        );
    }

    @Test
    void customizer() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.Customizer;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              public class config2 {
                  public SecurityFilterChain chain(HttpSecurity http) throws Exception {
                      this.customize(securityContext -> securityContext
                          .requireExplicitSave(true)
                      );
                      return http.build();
                  }

                  public void customize(Customizer<SecurityContextConfigurer<HttpSecurity>> securityContextCustomizer) {
                      // do something else
                  }
              }
              """,
            """
              import org.springframework.security.config.Customizer;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              public class config2 {
                  public SecurityFilterChain chain(HttpSecurity http) throws Exception {
                      this.customize(securityContext -> {}
                      );
                      return http.build();
                  }

                  public void customize(Customizer<SecurityContextConfigurer<HttpSecurity>> securityContextCustomizer) {
                      // do something else
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("CodeBlock2Expr")
    void onlyInChainWithBlockBody() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http.securityContext((securityContext) -> {
                              securityContext.requireExplicitSave(true);
                          });
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void withLeadingOuterChain() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http.securityContext(securityContext -> securityContext
                              .requireExplicitSave(false)
                          )
                          .securityContext(securityContext -> securityContext
                              .requireExplicitSave(true)
                          );
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              class T {
                  public SecurityFilterChain chain(HttpSecurity http) {
                      http.securityContext(securityContext -> securityContext
                              .requireExplicitSave(false)
                          );
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void unrelatedEmptyLambda() {
        // language=java
        rewriteRun(
          java(
            """
              class T {
                  public void m() {
                      java.util.function.Consumer<Object> consumer = (o) -> {};
                 }
              }
              """
          )
        );
    }

    @Test
    void callChainWithDifferentTypes() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.Customizer;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;

              public class T {
                  SecurityContextConfigurer<HttpSecurity> mySecurityContext;
                  OAuth2LoginConfigurer<HttpSecurity> myOAuth2;

                  public OAuth2LoginConfigurer<HttpSecurity> customize(
                      Customizer<SecurityContextConfigurer<HttpSecurity>> securityContextCustomizer) {
                      securityContextCustomizer.customize(mySecurityContext);
                      return myOAuth2;
                  }

                  public void doSomething(SecurityContextConfigurer<HttpSecurity> myConfigurer) {
                      OAuth2LoginConfigurer<HttpSecurity> auth = this.customize(securityContext -> {
                          securityContext.requireExplicitSave(true);
                      }).permitAll();
                  }
              }
              """,
            """
              import org.springframework.security.config.Customizer;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;

              public class T {
                  SecurityContextConfigurer<HttpSecurity> mySecurityContext;
                  OAuth2LoginConfigurer<HttpSecurity> myOAuth2;

                  public OAuth2LoginConfigurer<HttpSecurity> customize(
                      Customizer<SecurityContextConfigurer<HttpSecurity>> securityContextCustomizer) {
                      securityContextCustomizer.customize(mySecurityContext);
                      return myOAuth2;
                  }

                  public void doSomething(SecurityContextConfigurer<HttpSecurity> myConfigurer) {
                      OAuth2LoginConfigurer<HttpSecurity> auth = this.customize(securityContext -> {}).permitAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void customize() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.Customizer;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              public class config2 {
                  public SecurityFilterChain chain(HttpSecurity http) throws Exception {
                      this.customize(securityContext -> securityContext
                          .requireExplicitSave(true)
                      );
                      return http.build();
                  }

                  public void customize(Customizer<SecurityContextConfigurer<HttpSecurity>> securityContextCustomizer) {
                      // do something else
                  }
              }
              """,
            """
              import org.springframework.security.config.Customizer;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
              import org.springframework.security.web.SecurityFilterChain;

              public class config2 {
                  public SecurityFilterChain chain(HttpSecurity http) throws Exception {
                      this.customize(securityContext -> {}
                      );
                      return http.build();
                  }

                  public void customize(Customizer<SecurityContextConfigurer<HttpSecurity>> securityContextCustomizer) {
                      // do something else
                  }
              }
              """
          )
        );
    }

    @Disabled
    @Test
    void lambdaAssignment() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.Customizer;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;

              public class config2 {
                  void method() {
                      SecurityContextConfigurer<HttpSecurity> configurer = new SecurityContextConfigurer<>();
                      Customizer<SecurityContextConfigurer<HttpSecurity>> customizer = x -> configurer.requireExplicitSave(true);
                      customizer.toString();
                  }
              }
              """,
            """

              import org.springframework.security.config.Customizer;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;

              public class config2 {
                  void method() {
                      SecurityContextConfigurer<HttpSecurity> configurer = new SecurityContextConfigurer<>();
                      Customizer<SecurityContextConfigurer<HttpSecurity>> customizer = x -> {};
                      customizer.toString();
                  }
              }
              """
          )
        );
    }
}
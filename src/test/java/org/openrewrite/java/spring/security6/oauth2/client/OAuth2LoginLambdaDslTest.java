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
package org.openrewrite.java.spring.security6.oauth2.client;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class OAuth2LoginLambdaDslTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new OAuth2LoginLambdaDsl())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-2.4",
              "spring-beans-5.3", "spring-context-5.3", "spring-web-5.3", "spring-webmvc-5.3", "spring-core-5.3",
              "spring-security-core-5.5","spring-security-config-5.5","spring-security-web-5.5",
              "tomcat-embed"));
    }

    @DocumentExample
    @Test
    void advanced() {
        rewriteRun(
          //language=java
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
                              .oauth2Login(login -> login
                                      .tokenEndpoint()
                                              .accessTokenResponseClient(authorizationGrantRequest -> null)
                                              .and()
                                      .userInfoEndpoint()
                                              .userAuthoritiesMapper(authorities -> null));
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
                              .oauth2Login(login -> login
                                      .tokenEndpoint(endpoint -> endpoint
                                              .accessTokenResponseClient(authorizationGrantRequest -> null))
                                      .userInfoEndpoint(endpoint -> endpoint
                                              .userAuthoritiesMapper(authorities -> null)));
                  }
              }
              """
          )
        );
    }
}

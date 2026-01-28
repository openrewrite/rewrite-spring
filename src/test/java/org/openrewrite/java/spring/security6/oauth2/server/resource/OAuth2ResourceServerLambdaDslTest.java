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
package org.openrewrite.java.spring.security6.oauth2.server.resource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.junitpioneer.jupiter.Issue;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class OAuth2ResourceServerLambdaDslTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new OAuth2ResourceServerLambdaDsl())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-beans", "spring-context", "spring-boot", "spring-web", "spring-core",
              "spring-security-core-5", "spring-security-config-5", "spring-security-web-5",
              "tomcat-embed"))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-beans", "spring-context", "spring-boot", "spring-web", "spring-core",
              "spring-security-core-5", "spring-security-config-5", "spring-security-web-5",
              "tomcat-embed"))
          .typeValidationOptions(TypeValidation.all().identifiers(false));
    }

    @DisabledIfEnvironmentVariable(named = "CI", matches = "true")
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
                              .oauth2ResourceServer(server -> server
                                      .jwt()
                                              .jwkSetUri("")
                                              .and()
                                      .opaqueToken()
                                              .introspectionUri(""));
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
                              .oauth2ResourceServer(server -> server
                                      .jwt(jwt -> jwt
                                              .jwkSetUri(""))
                                      .opaqueToken(token -> token
                                              .introspectionUri("")));
                  }
              }
              """
          )
        );
    }

    @Nested
    class Kotlin {
        @Issue("https://github.com/moderneinc/customer-requests/issues/1765")
        @Test
        void preservesCustomJwtConfiguration() {
            rewriteRun(
              //language=kotlin
              kotlin(
                """
                  import org.springframework.security.config.annotation.web.builders.HttpSecurity
                  import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
                  import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

                  @EnableWebSecurity
                  class SecurityConfig : WebSecurityConfigurerAdapter() {
                      @Throws(Exception::class)
                      override fun configure(http: HttpSecurity) {
                          http
                                  .oauth2ResourceServer { server ->
                                      server
                                          .jwt()
                                                  .jwkSetUri("https://example.com/.well-known/jwks.json")
                                                  .and()
                                          .opaqueToken()
                                                  .introspectionUri("https://example.com/introspect")
                                  }
                      }
                  }
                  """,
                """
                  import org.springframework.security.config.annotation.web.builders.HttpSecurity
                  import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
                  import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

                  @EnableWebSecurity
                  class SecurityConfig : WebSecurityConfigurerAdapter() {
                      @Throws(Exception::class)
                      override fun configure(http: HttpSecurity) {
                          http
                              .oauth2ResourceServer { server ->
                                  server
                                      .jwt({jwt ->jwt
                                          .jwkSetUri("https://example.com/.well-known/jwks.json")})
                                      .opaqueToken({token ->token
                                          .introspectionUri("https://example.com/introspect")})
                              }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void alreadyMigratedNoChange() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=kotlin
              kotlin(
                """
                  import org.springframework.security.config.annotation.web.builders.HttpSecurity
                  import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
                  import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

                  @EnableWebSecurity
                  class SecurityConfig : WebSecurityConfigurerAdapter() {
                      @Throws(Exception::class)
                      override fun configure(http: HttpSecurity) {
                          http
                                  .oauth2ResourceServer { server ->
                                      server
                                          .jwt { jwt ->
                                                  jwt
                                                      .jwkSetUri("https://example.com/.well-known/jwks.json")
                                          }
                                          .opaqueToken { token ->
                                                  token
                                                      .introspectionUri("https://example.com/introspect")
                                          }
                                  }
                      }
                  }
                  """
              )
            );
        }
    }
}

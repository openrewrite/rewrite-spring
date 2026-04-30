/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.doc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class MigrateSpringFoxSecurityConfigurationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateSpringFoxSecurityConfiguration())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-context-5.+")
            //language=java
            .dependsOn(
              """
                package springfox.documentation.swagger.web;
                public class SecurityConfiguration {
                }
                """,
              """
                package springfox.documentation.swagger.web;
                import java.util.Map;
                public class SecurityConfigurationBuilder {
                    public static SecurityConfigurationBuilder builder() { return new SecurityConfigurationBuilder(); }
                    public SecurityConfigurationBuilder clientId(String s) { return this; }
                    public SecurityConfigurationBuilder clientSecret(String s) { return this; }
                    public SecurityConfigurationBuilder realm(String s) { return this; }
                    public SecurityConfigurationBuilder appName(String s) { return this; }
                    public SecurityConfigurationBuilder apiKey(String s) { return this; }
                    public SecurityConfigurationBuilder apiKeyVehicle(String s) { return this; }
                    public SecurityConfigurationBuilder apiKeyName(String s) { return this; }
                    public SecurityConfigurationBuilder scopeSeparator(String s) { return this; }
                    public SecurityConfigurationBuilder additionalQueryStringParams(Map<String, Object> m) { return this; }
                    public SecurityConfigurationBuilder useBasicAuthenticationWithAccessCodeGrant(Boolean b) { return this; }
                    public SecurityConfigurationBuilder enableCsrfSupport(Boolean b) { return this; }
                    public SecurityConfiguration build() { return new SecurityConfiguration(); }
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void enableCsrfSupportToProperty() {
        rewriteRun(
          srcMainResources(
            //language=properties
            properties(
              """
                spring.application.name=demo
                """,
              """
                spring.application.name=demo
                springdoc.swagger-ui.csrf.enabled=true
                """,
              spec -> spec.path("application.properties")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                package com.example;

                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import springfox.documentation.swagger.web.SecurityConfiguration;
                import springfox.documentation.swagger.web.SecurityConfigurationBuilder;

                @Configuration
                public class SwaggerConfig {

                    @Bean
                    SecurityConfiguration security() {
                        return SecurityConfigurationBuilder.builder()
                                .enableCsrfSupport(true)
                                .build();
                    }
                }
                """,
              """
                package com.example;

                import org.springframework.context.annotation.Configuration;

                @Configuration
                public class SwaggerConfig {
                }
                """
            )
          )
        );
    }

    @Test
    void multipleBuilderCallsToProperties() {
        rewriteRun(
          srcMainResources(
            //language=properties
            properties(
              """
                """,
              """
                springdoc.swagger-ui.csrf.enabled=true
                springdoc.swagger-ui.oauth.client-id=demo-client
                springdoc.swagger-ui.oauth.realm=demo-realm
                """,
              spec -> spec.path("application.properties")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                import org.springframework.context.annotation.Bean;
                import springfox.documentation.swagger.web.SecurityConfiguration;
                import springfox.documentation.swagger.web.SecurityConfigurationBuilder;

                class SwaggerConfig {

                    @Bean
                    SecurityConfiguration security() {
                        return SecurityConfigurationBuilder.builder()
                                .clientId("demo-client")
                                .realm("demo-realm")
                                .enableCsrfSupport(true)
                                .build();
                    }
                }
                """,
              """
                class SwaggerConfig {
                }
                """
            )
          )
        );
    }

    @Test
    void writesYamlWhenYamlExists() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: demo
                """,
              """
                spring.application.name: demo
                springdoc:
                  swagger-ui:
                    csrf:
                      enabled: true
                """,
              spec -> spec.path("application.yml")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                import org.springframework.context.annotation.Bean;
                import springfox.documentation.swagger.web.SecurityConfiguration;
                import springfox.documentation.swagger.web.SecurityConfigurationBuilder;

                class SwaggerConfig {

                    @Bean
                    SecurityConfiguration security() {
                        return SecurityConfigurationBuilder.builder()
                                .enableCsrfSupport(true)
                                .build();
                    }
                }
                """,
              """
                class SwaggerConfig {
                }
                """
            )
          )
        );
    }

    @Test
    void leavesNonLiteralArgumentAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import springfox.documentation.swagger.web.SecurityConfiguration;
              import springfox.documentation.swagger.web.SecurityConfigurationBuilder;

              class SwaggerConfig {

                  static final boolean CSRF = true;

                  @Bean
                  SecurityConfiguration security() {
                      return SecurityConfigurationBuilder.builder()
                              .enableCsrfSupport(CSRF)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesUnsupportedBuilderMethodAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import springfox.documentation.swagger.web.SecurityConfiguration;
              import springfox.documentation.swagger.web.SecurityConfigurationBuilder;

              class SwaggerConfig {

                  @Bean
                  SecurityConfiguration security() {
                      return SecurityConfigurationBuilder.builder()
                              .apiKey("api_key")
                              .apiKeyName("X-API-KEY")
                              .apiKeyVehicle("header")
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesNonBeanMethodAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.swagger.web.SecurityConfiguration;
              import springfox.documentation.swagger.web.SecurityConfigurationBuilder;

              class SwaggerConfig {

                  SecurityConfiguration security() {
                      return SecurityConfigurationBuilder.builder()
                              .enableCsrfSupport(true)
                              .build();
                  }
              }
              """
          )
        );
    }
}

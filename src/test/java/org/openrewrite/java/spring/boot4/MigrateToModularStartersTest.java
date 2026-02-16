/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateToModularStartersTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource(
          "/META-INF/rewrite/spring-boot-40-modular-starters.yml",
          "org.openrewrite.java.spring.boot4.MigrateToModularStarters"
        ).parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-autoconfigure-3",
            "spring-boot-3",
            "spring-boot-test-3",
            "spring-boot-test-autoconfigure-3",
            "spring-beans-6",
            "spring-context-6",
            "spring-web-6",
            "spring-core-6"));
    }

    @DocumentExample
    @Test
    void migrateLiquibaseToStarterInMaven() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.liquibase</groupId>
                          <artifactId>liquibase-core</artifactId>
                          <version>4.24.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .doesNotContain("<artifactId>liquibase-core</artifactId>")
              .contains("<artifactId>spring-boot-starter-liquibase</artifactId>")
              .containsPattern("<version>4\\.0\\.\\d+</version>")
              .actual())
          )
        );
    }

    @Test
    void migrateSpringSecurityTestToStarterInMaven() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.security</groupId>
                          <artifactId>spring-security-test</artifactId>
                          <version>6.0.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .doesNotContain("<artifactId>spring-security-test</artifactId>")
              .contains("<artifactId>spring-boot-starter-security-test</artifactId>")
              .containsPattern("<version>4\\.0\\.\\d+</version>")
              .actual())
          )
        );
    }

    @Test
    void migrateSpringKakfaToStarterInMaven() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.kafka</groupId>
                          <artifactId>spring-kafka</artifactId>
                          <version>3.3.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .doesNotContain("<artifactId>spring-kafka</artifactId>")
              .contains("<artifactId>spring-boot-starter-kafka</artifactId>")
              .containsPattern("<version>4\\.0\\.\\d+</version>")
              .actual())
          )
        );
    }

    @Test
    void migrateSpringBootPersistencePackages() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.boot.autoconfigure.domain.EntityScan;

              @Configuration
              @EntityScan("x.y.z")
              class PersistenceConfig {
              }
              """
            ,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.boot.persistence.autoconfigure.EntityScan;

              @Configuration
              @EntityScan("x.y.z")
              class PersistenceConfig {
              }
              """
          )
        );
    }

    @Nested
    class RestClientStarter {

        @Test
        void addRestClientStarterIfRestTemplateIsUsed() {
            rewriteRun(
              mavenProject("project",
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>example</artifactId>
                        <version>1.0-SNAPSHOT</version>
                        <dependencies>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(pom -> assertThat(pom)
                    .contains("<artifactId>spring-boot-starter-restclient</artifactId>")
                    .containsPattern("<version>4\\.0\\.\\d+</version>")
                    .actual())
                ),
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.springframework.web.client.RestTemplate;

                      class A {
                          private RestTemplate rest;
                      }
                      """
                  )
                )
              )
            );
        }

        @Test
        void addRestClientStarterIfRestClientIsUsed() {
            rewriteRun(
              mavenProject("project",
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>example</artifactId>
                        <version>1.0-SNAPSHOT</version>
                        <dependencies>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(pom -> assertThat(pom)
                    .contains("<artifactId>spring-boot-starter-restclient</artifactId>")
                    .containsPattern("<version>4\\.0\\.\\d+</version>")
                    .actual())
                ),
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.springframework.web.client.RestClient;

                      class A {
                          private RestClient rest;
                      }
                      """
                  )
                )
              )
            );
        }

        @Test
        void addRestClientStarterIfRestTemplateBuilderIsUsed() {
            rewriteRun(
              mavenProject("project",
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.example</groupId>
                        <artifactId>example</artifactId>
                        <version>1.0-SNAPSHOT</version>
                        <dependencies>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(pom -> assertThat(pom)
                    .contains("<artifactId>spring-boot-starter-restclient</artifactId>")
                    .containsPattern("<version>4\\.0\\.\\d+</version>")
                    .actual())
                ),
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.springframework.boot.web.client.RestTemplateBuilder;

                      class A {
                          private RestTemplateBuilder restBuilder;
                      }
                      """,
                    """
                      import org.springframework.boot.restclient.RestTemplateBuilder;

                      class A {
                          private RestTemplateBuilder restBuilder;
                      }
                      """
                  )
                )
              )
            );
        }
    }

    @Test
    void addRestTestClientTestDependencyIfTestRestTemplateIsUsedForTest() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>spring-boot-resttestclient</artifactId>")
                .contains("<scope>test</scope>")
                .containsPattern("<version>4\\.0\\.\\d+</version>")
                .actual())
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.springframework.boot.test.web.client.TestRestTemplate;

                  class A {
                      private TestRestTemplate rest;
                  }
                  """,
                """
                  import org.springframework.boot.resttestclient.TestRestTemplate;

                  class A {
                      private TestRestTemplate rest;
                  }
                  """
              )
            )
          )
        );
    }

    @Nested
    class MigrateActuateHealthPackage implements RewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            spec.recipeFromResource(
              "/META-INF/rewrite/spring-boot-40-modular-starters.yml",
              "org.openrewrite.java.spring.boot4.MigrateToModularStarters"
            ).parser(JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(),
                "spring-boot-actuator-3"));
        }

        @Test
        void migrateActuateHealthToHealthContributor() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.boot.actuate.health.AbstractHealthIndicator;
                  import org.springframework.boot.actuate.health.Health;
                  import org.springframework.boot.actuate.health.HealthIndicator;

                  class MyHealthIndicator extends AbstractHealthIndicator {
                      @Override
                      protected void doHealthCheck(Health.Builder builder) {
                          builder.up().build();
                      }
                  }
                  """,
                """
                  import org.springframework.boot.health.contributor.AbstractHealthIndicator;
                  import org.springframework.boot.health.contributor.Health;
                  import org.springframework.boot.health.contributor.HealthIndicator;

                  class MyHealthIndicator extends AbstractHealthIndicator {
                      @Override
                      protected void doHealthCheck(Health.Builder builder) {
                          builder.up().build();
                      }
                  }
                  """
              )
            );
        }

    }

    @Nested
    class MigrateAutoconfigurePackages {
        @Test
        void migrateSecurityPropertiesConstants() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.boot.autoconfigure.security.SecurityProperties;

                  class A {
                      private final int basicOrder = SecurityProperties.BASIC_AUTH_ORDER;
                      private final int defaultOrder = SecurityProperties.DEFAULT_FILTER_ORDER;
                  }
                  """,
                """
                  import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;

                  class A {
                      private final int basicOrder = SecurityFilterProperties.BASIC_AUTH_ORDER;
                      private final int defaultOrder = SecurityFilterProperties.DEFAULT_FILTER_ORDER;
                  }
                  """
              )
            );
        }

        @Test
        void migrateSpringBootWebtestclient() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Collections;
                  import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
                  import org.springframework.boot.test.autoconfigure.web.reactive.SpringBootWebTestClientBuilderCustomizer;
                  import org.springframework.boot.test.autoconfigure.web.reactive.WebTestClientAutoConfiguration;
                  import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer;

                  @AutoConfigureWebTestClient
                  class WebClientTest {
                      private final WebTestClientAutoConfiguration webTestClientAutoConfiguration = new WebTestClientAutoConfiguration();
                      private final SpringBootWebTestClientBuilderCustomizer springBootWebTestClientBuilderCustomizer = new SpringBootWebTestClientBuilderCustomizer(Collections.emptyList());
                      private final WebTestClientBuilderCustomizer webTestClientBuilderCustomizer = builder -> {};
                  }
                  """
                ,
                """
                  import java.util.Collections;
                  import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
                  import org.springframework.boot.webtestclient.autoconfigure.SpringBootWebTestClientBuilderCustomizer;
                  import org.springframework.boot.webtestclient.autoconfigure.WebTestClientAutoConfiguration;
                  import org.springframework.boot.webtestclient.autoconfigure.WebTestClientBuilderCustomizer;

                  @AutoConfigureWebTestClient
                  class WebClientTest {
                      private final WebTestClientAutoConfiguration webTestClientAutoConfiguration = new WebTestClientAutoConfiguration();
                      private final SpringBootWebTestClientBuilderCustomizer springBootWebTestClientBuilderCustomizer = new SpringBootWebTestClientBuilderCustomizer(Collections.emptyList());
                      private final WebTestClientBuilderCustomizer webTestClientBuilderCustomizer = builder -> {};
                  }
                  """
              )
            );
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"flyway-database-postgresql", "flyway-mysql"})
    void addFlywayStarterWhenDependencyPresent(String artifactId) {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("sample",
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>sample-module</module>
                  </modules>
                </project>
                """
            ),
            mavenProject("sample-module",
              srcMainJava(
                java(
                  """
                    class AnyClass {
                        String s = "";
                    }
                    """
                )
              ),
              pomXml(
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.sample</groupId>
                        <artifactId>sample</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>sample-module</artifactId>
                    <dependencies>
                      <dependency>
                        <groupId>org.flywaydb</groupId>
                        <artifactId>%s</artifactId>
                        <version>10.0.0</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """.formatted(artifactId),
                spec -> spec.after(pom -> assertThat(pom)
                  .contains("<groupId>org.flywaydb</groupId>")
                  .contains("<artifactId>%s</artifactId>".formatted(artifactId))
                  .contains("<artifactId>spring-boot-starter-flyway</artifactId>")
                  .containsPattern("<version>4\\.0\\.\\d+</version>")
                  .actual())
              ),
              buildGradle(
                """
                  plugins {
                      id 'java'
                  }

                  repositories {
                      mavenCentral()
                  }

                  dependencies {
                      implementation('org.springframework.boot:spring-boot-starter-actuator')
                  }
                  """),
              buildGradle(
                """
                        plugins {
                            id 'java'
                        }

                        repositories {
                            mavenCentral()
                        }

                        dependencies {
                            implementation('org.flywaydb:%s:10.0.0')
                        }
                  """.formatted(artifactId),
                spec -> spec.after(gradle -> assertThat(gradle)
                  .contains("implementation('org.flywaydb:%s:10.0.0')".formatted(artifactId))
                  .contains("org.springframework.boot:spring-boot-starter-flyway")
                  .containsPattern("4\\.0\\.\\d+")
                  .actual())
              )
            )
          )
        );
    }
}

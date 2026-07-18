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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateMongoDbModularStartersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource(
            "/META-INF/rewrite/spring-boot-40-modular-starters.yml",
            "org.openrewrite.java.spring.boot4.MigrateToModularStarters"
          )
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-autoconfigure-3",
              "spring-boot-3",
              "spring-boot-test-3",
              "spring-boot-test-autoconfigure-3",
              "spring-beans-6",
              "spring-context-6",
              "spring-web-6",
              "spring-core-6")
            .dependsOn(
              """
                package org.springframework.boot.actuate.autoconfigure.data.mongo;
                public class MongoHealthContributorAutoConfiguration {
                }
                """,
              """
                package org.springframework.boot.actuate.autoconfigure.data.mongo;
                public class MongoReactiveHealthContributorAutoConfiguration {
                }
                """,
              """
                package org.springframework.boot.actuate.autoconfigure.metrics.mongo;
                public class MongoMetricsAutoConfiguration {
                }
                """,
              """
                package org.springframework.boot.actuate.data.mongo;
                public class MongoHealthIndicator {
                }
                """,
              """
                package org.springframework.boot.actuate.data.mongo;
                public class MongoReactiveHealthIndicator {
                }
                """
            ))
          .typeValidationOptions(TypeValidation.none());
    }

    @DocumentExample
    @Test
    void addImperativeMongoDbTestStarter() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-mongodb</artifactId>
                            <version>3.5.14</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>spring-boot-starter-data-mongodb-test</artifactId>")
                .doesNotContain("<artifactId>spring-boot-starter-data-mongodb-reactive-test</artifactId>")
                .contains("<scope>test</scope>")
                .containsPattern("<version>4\\.0\\.\\d+</version>")
                .actual())
            ),
            srcTestJava(
              java(
                """
                  import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

                  @DataMongoTest
                  class RepositoryTest {
                  }
                  """,
                """
                  import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;

                  @DataMongoTest
                  class RepositoryTest {
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void addReactiveMongoDbTestStarter() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
                            <version>3.5.14</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>spring-boot-starter-data-mongodb-reactive-test</artifactId>")
                .doesNotContain("<artifactId>spring-boot-starter-data-mongodb-test</artifactId>")
                .contains("<scope>test</scope>")
                .containsPattern("<version>4\\.0\\.\\d+</version>")
                .actual())
            ),
            srcTestJava(
              java(
                """
                  import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

                  @DataMongoTest
                  class ReactiveRepositoryTest {
                  }
                  """,
                """
                  import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;

                  @DataMongoTest
                  class ReactiveRepositoryTest {
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void doNotAddMongoDbTestStarterWithoutMongoDbTestSliceUsage() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-mongodb</artifactId>
                            <version>3.5.14</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.afterRecipe(pom -> assertThat(pom.printAll())
                .doesNotContain("spring-boot-starter-data-mongodb-test")
                .doesNotContain("spring-boot-starter-data-mongodb-reactive-test"))
            )
          )
        );
    }


    @Test
    void doNotAddReactiveMongoDbTestStarterWithoutMongoDbTestSliceUsage() {
      rewriteRun(
        mavenProject("project",
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
                          <version>3.5.14</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.afterRecipe(pom -> assertThat(pom.printAll())
              .doesNotContain("spring-boot-starter-data-mongodb-test")
              .doesNotContain("spring-boot-starter-data-mongodb-reactive-test"))
          )
        )
      );
    }

    @Test
    void migrateMongoDbAutoConfigurationTypes() {
        rewriteRun(
          java(
            """
              import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
              import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
              import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration;
              import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;

              class MongoConfigurations {
                  Class<?>[] configurations = {
                      MongoDataAutoConfiguration.class,
                      MongoReactiveDataAutoConfiguration.class,
                      MongoRepositoriesAutoConfiguration.class,
                      MongoReactiveRepositoriesAutoConfiguration.class
                  };
              }
              """,
            """
              import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
              import org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveAutoConfiguration;
              import org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveRepositoriesAutoConfiguration;
              import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration;

              class MongoConfigurations {
                  Class<?>[] configurations = {
                      DataMongoAutoConfiguration.class,
                      DataMongoReactiveAutoConfiguration.class,
                      DataMongoRepositoriesAutoConfiguration.class,
                      DataMongoReactiveRepositoriesAutoConfiguration.class
                  };
              }
              """
          )
        );
    }

    @Test
    void migrateMongoDbActuatorPackages() {
        rewriteRun(
          java(
            """
              import org.springframework.boot.actuate.autoconfigure.data.mongo.MongoHealthContributorAutoConfiguration;
              import org.springframework.boot.actuate.autoconfigure.data.mongo.MongoReactiveHealthContributorAutoConfiguration;
              import org.springframework.boot.actuate.autoconfigure.metrics.mongo.MongoMetricsAutoConfiguration;
              import org.springframework.boot.actuate.data.mongo.MongoHealthIndicator;
              import org.springframework.boot.actuate.data.mongo.MongoReactiveHealthIndicator;

              class MongoActuatorTypes {
                  MongoHealthContributorAutoConfiguration health;
                  MongoReactiveHealthContributorAutoConfiguration reactiveHealth;
                  MongoMetricsAutoConfiguration metrics;
                  MongoHealthIndicator indicator;
                  MongoReactiveHealthIndicator reactiveIndicator;
              }
              """,
            """
              import org.springframework.boot.mongodb.autoconfigure.health.MongoHealthContributorAutoConfiguration;
              import org.springframework.boot.mongodb.autoconfigure.health.MongoReactiveHealthContributorAutoConfiguration;
              import org.springframework.boot.mongodb.autoconfigure.metrics.MongoMetricsAutoConfiguration;
              import org.springframework.boot.mongodb.health.MongoHealthIndicator;
              import org.springframework.boot.mongodb.health.MongoReactiveHealthIndicator;

              class MongoActuatorTypes {
                  MongoHealthContributorAutoConfiguration health;
                  MongoReactiveHealthContributorAutoConfiguration reactiveHealth;
                  MongoMetricsAutoConfiguration metrics;
                  MongoHealthIndicator indicator;
                  MongoReactiveHealthIndicator reactiveIndicator;
              }
              """
          )
        );
    }
}

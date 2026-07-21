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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateSpringDataMongoDb5Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.data.MigrateSpringDataMongoDb5")
          .beforeRecipe(withToolingApi());
    }

    @DocumentExample
    @Test
    void upgradesExplicitSpringDataMongoDbDependency() {
        rewriteRun(
          mavenProject("spring-data-mongodb",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.data</groupId>
                            <artifactId>spring-data-mongodb</artifactId>
                            <version>4.5.13</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> {
                  assertDependencyVersion(actual, "spring-data-mongodb", "5\\.0\\.\\d+");
                  return actual;
              })
            ),
            java("class Application {}")
          )
        );
    }

    @ParameterizedTest
    @CsvSource({
      "mongodb-driver-core,4.11.5",
      "mongodb-driver-sync,4.11.5",
      "mongodb-driver-reactivestreams,4.11.5",
      "mongodb-crypt,1.11.0",
      "bson,4.11.5",
      "bson-record-codec,4.11.5",
      "mongodb-driver-legacy,4.11.5"
    })
    void upgradesExplicitMongoDbDriverDependency(String artifactId, String currentVersion) {
        rewriteRun(
          mavenProject("mongodb-driver-" + artifactId,
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.data</groupId>
                            <artifactId>spring-data-mongodb</artifactId>
                            <version>4.5.13</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>%s</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(artifactId, currentVersion),
              spec -> spec.after(actual -> {
                  assertDependencyVersion(actual, artifactId, "5\\.6\\.\\d+");
                  return actual;
              })
            ),
            java("class Application {}")
          )
        );
    }

    @Test
    void upgradesExplicitGradleDependencies() {
        rewriteRun(
          mavenProject("explicit-gradle-dependencies",
            buildGradle(
              //language=groovy
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'org.springframework.data:spring-data-mongodb:4.5.13'
                    implementation 'org.mongodb:mongodb-driver-reactivestreams:5.5.1'
                    implementation 'org.mongodb:bson-record-codec:4.11.5'
                    implementation 'org.mongodb:mongodb-driver-legacy:4.11.5'
                }
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .containsPattern("spring-data-mongodb:5\\.0\\.\\d+")
                  .containsPattern("mongodb-driver-reactivestreams:5\\.6\\.\\d+")
                  .containsPattern("bson-record-codec:5\\.6\\.\\d+")
                  .containsPattern("mongodb-driver-legacy:5\\.6\\.\\d+")
                  .actual())
            ),
            java("class Application {}")
          )
        );
    }

    @Test
    void upgradesExplicitMongoDbBomAndLeavesManagedComponentsVersionless() {
        rewriteRun(
          mavenProject("mongodb-driver-bom",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.mongodb</groupId>
                                <artifactId>mongodb-driver-bom</artifactId>
                                <version>5.5.1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.data</groupId>
                            <artifactId>spring-data-mongodb</artifactId>
                            <version>4.5.13</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>bson-record-codec</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> {
                  assertDependencyVersion(actual, "mongodb-driver-bom", "5\\.6\\.\\d+");
                  assertThat(actual)
                    .doesNotContainPattern("<artifactId>mongodb-driver-sync</artifactId>\\s*<version>")
                    .doesNotContainPattern("<artifactId>bson-record-codec</artifactId>\\s*<version>");
                  return actual;
              })
            ),
            java("class Application {}")
          )
        );
    }

    @Test
    void leavesBootManagedVersionlessDependenciesUnchanged() {
        rewriteRun(
          mavenProject("managed-dependencies",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.0.7</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.data</groupId>
                            <artifactId>spring-data-mongodb</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            java("class Application {}")
          )
        );
    }

    @Test
    void removesOverridesThatBecomeRedundantWithBootManagement() {
        rewriteRun(
          mavenProject("managed-overrides",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.0.7</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.data</groupId>
                            <artifactId>spring-data-mongodb</artifactId>
                            <version>4.5.13</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                            <version>4.11.5</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.0.7</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.data</groupId>
                            <artifactId>spring-data-mongodb</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            java("class Application {}")
          )
        );
    }

    @Test
    void upgradesDriverOverrideWhenSpringDataMongoDbIsTransitive() {
        rewriteRun(
          mavenProject("boot-starter-with-driver-override",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.0</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-mongodb</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                            <version>4.11.5</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> {
                  assertDependencyVersion(actual, "mongodb-driver-sync", "5\\.6\\.\\d+");
                  assertThat(actual).doesNotContainPattern(
                    "<artifactId>spring-boot-starter-data-mongodb</artifactId>\\s*<version>");
                  return actual;
              })
            ),
            java("class Application {}")
          )
        );
    }

    @Test
    void preservesCompatibleForwardOverrides() {
        rewriteRun(
          mavenProject("forward-overrides",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.data</groupId>
                            <artifactId>spring-data-mongodb</artifactId>
                            <version>5.1.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                            <version>5.8.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            java("class Application {}")
          )
        );
    }

    @Test
    void leavesNonAllowListedJvmDriverArtifactsUnchanged() {
        rewriteRun(
          mavenProject("other-jvm-drivers",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.0.7</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.data</groupId>
                            <artifactId>spring-data-mongodb</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongo-java-driver</artifactId>
                            <version>3.12.14</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-kotlin-coroutine</artifactId>
                            <version>5.5.1</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-kotlin-sync</artifactId>
                            <version>5.5.1</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>bson-kotlin</artifactId>
                            <version>5.5.1</version>
                        </dependency>
                        <dependency>
                            <groupId>org.mongodb.scala</groupId>
                            <artifactId>mongo-scala-driver_2.13</artifactId>
                            <version>5.5.1</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            java("class Application {}")
          )
        );
    }

    @Test
    void doesNotUpgradeMongoDriverOutsideSpringDataMongoDbModules() {
        rewriteRun(
          mavenProject("driver-only",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                            <version>4.11.5</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            java("class Application {}")
          )
        );
    }

    private static void assertDependencyVersion(String pom, String artifactId, String versionPattern) {
        assertThat(pom).containsPattern(
          "<artifactId>" + artifactId + "</artifactId>\\s*<version>" + versionPattern + "</version>");
    }
}

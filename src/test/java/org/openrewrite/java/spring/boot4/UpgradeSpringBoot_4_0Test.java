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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringBoot_4_0Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0")
          .beforeRecipe(withToolingApi());
    }

    @Test
    void upgradeKotlinPluginInKotlinDsl() {
        rewriteRun(
          buildGradleKts(
            """
              plugins {
                  val kotlinVersion = "2.1.21"
                  id("org.springframework.boot") version "3.5.7"
                  id("io.spring.dependency-management") version "1.1.7"
                  kotlin("jvm") version kotlinVersion
                  kotlin("plugin.spring") version kotlinVersion
              }
              """,
            spec -> spec.after(actual -> {
                return assertThat(actual)
                  .describedAs("Kotlin version should be bumped to 2.2.x for Spring Boot 4")
                  .containsPattern("val kotlinVersion = \"2\\.2\\.\\d+\"").actual();
            })
          )
        );
    }

    @Test
    void upgradeKotlinMavenPluginVersionProperty() {
        // When kotlin-maven-plugin carries an explicit <version>${kotlin.version}</version>
        // and is not BOM-managed (no Spring Boot parent here), UpgradePluginVersion follows
        // the property reference and bumps the property to a 2.2.x release.
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>kotlin-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <properties>
                        <kotlin.version>2.1.21</kotlin.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.5.7</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>${kotlin.version}</version>
                                <configuration>
                                    <args>
                                        <arg>-Xjsr305=strict</arg>
                                    </args>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              spec -> spec.after(actual -> {
                  return assertThat(actual)
                    .describedAs("kotlin.version property should be bumped to 2.2.x")
                    .containsPattern("<kotlin\\.version>2\\.2\\.\\d+</kotlin\\.version>").actual();
              })
            )
          )
        );
    }

    @Test
    void upgradeJobRunrStarterToSpringBoot4Starter() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>jobrunr-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.5.7</version>
                        </dependency>
                        <dependency>
                            <groupId>org.jobrunr</groupId>
                            <artifactId>jobrunr-spring-boot-3-starter</artifactId>
                            <version>7.5.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .describedAs("JobRunr Spring Boot 3 starter should be renamed to the Spring Boot 4 starter at 8.x")
                  .contains("jobrunr-spring-boot-4-starter")
                  .doesNotContain("jobrunr-spring-boot-3-starter")
                  .containsPattern("<artifactId>jobrunr-spring-boot-4-starter</artifactId>\\s*<version>8\\.\\d+(\\.\\d+)?</version>")
                  .actual())
            )
          )
        );
    }

    @Test
    void upgradeJobRunrCoreDependency() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>jobrunr-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.5.7</version>
                        </dependency>
                        <dependency>
                            <groupId>org.jobrunr</groupId>
                            <artifactId>jobrunr</artifactId>
                            <version>7.5.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .describedAs("JobRunr core library should be upgraded to 8.x")
                  .containsPattern("<artifactId>jobrunr</artifactId>\\s*<version>8\\.\\d+(\\.\\d+)?</version>")
                  .actual())
            )
          )
        );
    }

    @Test
    void upgradeJobRunrStarterInGradle() {
        rewriteRun(
          mavenProject("project",
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.5.7'
                    id 'io.spring.dependency-management' version '1.1.7'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'org.jobrunr:jobrunr-spring-boot-3-starter:7.5.0'
                }
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .describedAs("JobRunr Spring Boot 3 starter should be renamed to the Spring Boot 4 starter at 8.x")
                  .contains("jobrunr-spring-boot-4-starter")
                  .doesNotContain("jobrunr-spring-boot-3-starter")
                  .containsPattern("org\\.jobrunr:jobrunr-spring-boot-4-starter:8\\.\\d+(\\.\\d+)?")
                  .actual())
            )
          )
        );
    }

    @Test
    void jacksonBomVersionPropertyMigratedToJackson2Bom() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.14</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>jackson-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <properties>
                        <jackson-bom.version>2.21.1</jackson-bom.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.dataformat</groupId>
                            <artifactId>jackson-dataformat-xml</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .describedAs("Jackson dependencies should stay versionless (managed by Spring Boot BOM)")
                .contains("<groupId>tools.jackson.core</groupId>")
                .contains("<groupId>tools.jackson.dataformat</groupId>")
                .doesNotContain("<version>3")
                .describedAs("The Spring Boot 3 jackson-bom.version override (a Jackson 2 version) should migrate to jackson-2-bom.version")
                .contains("<jackson-2-bom.version>2.21.1</jackson-2-bom.version>")
                .doesNotContain("<jackson-bom.version>")
                .actual())
            )
          )
        );
    }

    @Test
    void jacksonDependenciesStayManagedWithoutOverrideProperty() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.14</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>jackson-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .describedAs("Jackson dependencies should stay versionless (managed by Spring Boot BOM)")
                .contains("<groupId>tools.jackson.core</groupId>")
                .doesNotContain("<version>3")
                .actual())
            )
          )
        );
    }

    @Test
    void jacksonBomVersionOverrideOnAlreadyBoot4ProjectLeftUntouched() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.0.0</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>jackson-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <properties>
                        <jackson-bom.version>3.1.0</jackson-bom.version>
                    </properties>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .describedAs("A deliberate Jackson 3 override on an already-Spring-Boot-4 project must not be renamed to jackson-2-bom.version")
                .contains("<jackson-bom.version>3.1.0</jackson-bom.version>")
                .doesNotContain("jackson-2-bom.version")
                .actual())
            )
          )
        );
    }

    @Test
    void removeOverrideOvertakenByBoot4Bom() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.14</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>redundant-version-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <properties>
                        <commons-lang3-override.version>3.18.0</commons-lang3-override.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-lang3</artifactId>
                            <version>${commons-lang3-override.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .describedAs("A commons-lang3 override newer than the Boot 3.5 managed version (3.17.0) but " +
                             "older than the Boot 4 managed version (3.19.0) should be removed once on the Boot 4 BOM")
                .contains("<artifactId>commons-lang3</artifactId>")
                .doesNotContain("<version>${commons-lang3-override.version}</version>")
                .describedAs("The now-unused version property should also be removed")
                .doesNotContain("commons-lang3-override.version")
                .actual())
            )
          )
        );
    }

    @Test
    void preserveDependencyVersionNewerThanBoot4Bom() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.14</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>newer-override-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.google.code.gson</groupId>
                            <artifactId>gson</artifactId>
                            <version>2.14.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .describedAs("A gson override newer than the Spring Boot 4 managed version must be preserved")
                .contains("<version>2.14.0</version>")
                .actual())
            )
          )
        );
    }

    @Test
    void removeRedundantVersionAlreadyOnBoot4() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.0.7</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>redundant-version-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <properties>
                        <commons-lang3-override.version>3.18.0</commons-lang3-override.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-lang3</artifactId>
                            <version>${commons-lang3-override.version}</version>
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
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>redundant-version-app</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-lang3</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

}

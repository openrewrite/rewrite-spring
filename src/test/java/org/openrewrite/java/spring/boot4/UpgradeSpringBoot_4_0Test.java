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
                assertThat(actual)
                  .describedAs("Kotlin version should be bumped to 2.2.x for Spring Boot 4")
                  .containsPattern("val kotlinVersion = \"2\\.2\\.\\d+\"");
                return actual;
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
                  assertThat(actual)
                    .describedAs("kotlin.version property should be bumped to 2.2.x")
                    .containsPattern("<kotlin\\.version>2\\.2\\.\\d+</kotlin\\.version>");
                  return actual;
              })
            )
          )
        );
    }

}

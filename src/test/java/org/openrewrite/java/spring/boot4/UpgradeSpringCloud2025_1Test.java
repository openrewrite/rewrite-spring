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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringCloud2025_1Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.cloud2025.UpgradeSpringCloud_2025_1");
    }

    @DocumentExample
    @Test
    void upgradeSpringCloudBomFrom2025_0To2025_1() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>fooservice</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.cloud</groupId>
                                <artifactId>spring-cloud-dependencies</artifactId>
                                <version>2025.0.1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-starter-config</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .containsPattern("<version>2025\\.1\\.\\d+</version>")
                  .actual())
            )
          )
        );
    }

    @Test
    void migrateSpringCloudStarterParentToBootParentWithBom() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
          mavenProject("project",
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.cloud</groupId>
                        <artifactId>spring-cloud-starter-parent</artifactId>
                        <version>2025.0.0</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>fooservice</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-starter-config</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> {
                return assertThat(actual)
                          .containsPattern("<groupId>org.springframework.boot</groupId>\\s*<artifactId>spring-boot-starter-parent</artifactId>\\s*<version>4\\.0\\.\\d+</version>")
                          .containsPattern("<groupId>org.springframework.cloud</groupId>\\s*<artifactId>spring-cloud-dependencies</artifactId>\\s*<version>2025\\.1\\.\\d+</version>")
                          .doesNotContain("spring-cloud-starter-parent").actual();
              })
            )
          )
        );
    }

    @Test
    void noChangeWhenNoSpringCloudDependency() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>fooservice</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>3.4.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void upgradeStandaloneCloudDependencyTo5() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>fooservice</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-gateway-server-webflux</artifactId>
                            <version>4.3.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .containsPattern("5\\.0\\.\\d+")
                  .actual())
            )
          )
        );
    }
}

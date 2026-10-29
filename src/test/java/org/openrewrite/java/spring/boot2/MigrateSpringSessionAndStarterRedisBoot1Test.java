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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateSpringSessionAndStarterRedisBoot1Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot2.MigrateSpringSessionAndStarterRedisBoot1");
    }

    @DocumentExample
    @Test
    void renameSpringSessionAndStarterRedis() {
        rewriteRun(
          mavenProject("demo",
            srcMainJava(java("class Demo {}")),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>1.5.8.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-redis</artifactId>
                            <version>1.4.7.RELEASE</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.session</groupId>
                            <artifactId>spring-session</artifactId>
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
                        <version>1.5.8.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-redis</artifactId>
                            <version>1.4.7.RELEASE</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.session</groupId>
                            <artifactId>spring-session-data-redis</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void renameSpringSessionWithStarterDataRedis() {
        rewriteRun(
          mavenProject("demo",
            srcMainJava(java("class Demo {}")),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>1.5.8.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-redis</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.session</groupId>
                            <artifactId>spring-session</artifactId>
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
                        <version>1.5.8.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-redis</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.session</groupId>
                            <artifactId>spring-session-data-redis</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void renameInGradleBuild() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("demo",
            srcMainJava(java("class Demo {}")),
            buildGradle(
              """
                plugins {
                    id "java"
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation "org.springframework.boot:spring-boot-starter-redis:1.4.7.RELEASE"
                    implementation "org.springframework.session:spring-session:1.3.1.RELEASE"
                }
                """,
              """
                plugins {
                    id "java"
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation "org.springframework.boot:spring-boot-starter-data-redis:1.4.7.RELEASE"
                    implementation "org.springframework.session:spring-session-data-redis:1.3.1.RELEASE"
                }
                """
            )
          )
        );
    }

    @Test
    void renameAsPartOfUpgradeSpringBoot_2_0() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0"),
          mavenProject("demo",
            srcMainJava(java("class Demo {}")),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>1.5.8.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-redis</artifactId>
                            <version>1.4.7.RELEASE</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.session</groupId>
                            <artifactId>spring-session</artifactId>
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
                        <version>2.0.9.RELEASE</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-redis</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.session</groupId>
                            <artifactId>spring-session-data-redis</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void alreadyMigratedBoot2Dependencies() {
            rewriteRun(
              mavenProject("demo",
                srcMainJava(java("class Demo {}")),
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>2.0.9.RELEASE</version>
                        </parent>
                        <groupId>com.example</groupId>
                        <artifactId>demo</artifactId>
                        <version>1.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-data-redis</artifactId>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.session</groupId>
                                <artifactId>spring-session-data-redis</artifactId>
                            </dependency>
                        </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        @Test
        void springSessionWithoutRedis() {
            rewriteRun(
              mavenProject("demo",
                srcMainJava(java("class Demo {}")),
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>1.5.8.RELEASE</version>
                        </parent>
                        <groupId>com.example</groupId>
                        <artifactId>demo</artifactId>
                        <version>1.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-jdbc</artifactId>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.session</groupId>
                                <artifactId>spring-session</artifactId>
                            </dependency>
                        </dependencies>
                    </project>
                    """
                )
              )
            );
        }
    }
}

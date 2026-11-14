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
package org.openrewrite.java.spring.boot2.search;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class LoggingShutdownHooksTest implements RewriteTest {

    @Language("java")
    private final String app = """
          import org.springframework.boot.autoconfigure.SpringBootApplication;

          @SpringBootApplication
          class Application {}
      """;

    //language=java
    private SourceSpecs found() {
        return srcMainJava(
          java(app, """
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                /*~~>*/@SpringBootApplication
                class Application {}
            """)
        );
    }

    private SourceSpecs notFound() {
        return srcMainJava(
          java(app)
        );
    }


    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LoggingShutdownHooks())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans-5", "spring-boot-autoconfigure-2.7"));
    }

    @Test
    void noExplicitPackagingType() {
        rewriteRun(
          mavenProject("acme",
            found(),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.4.13</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>acme</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void explicitJarPackagingType() {
        rewriteRun(
          mavenProject("acme",
            found(),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.4.13</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>acme</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <packaging>jar</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void explicitWarPackagingType() {
        rewriteRun(
          mavenProject("acme",
            notFound(),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.4.13</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>acme</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <packaging>war</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void lowerBootProject() {
        rewriteRun(
          mavenProject("acme",
            notFound(),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.3.12.RELEASE</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>acme</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <packaging>war</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void higherBootProject() {
        rewriteRun(
          mavenProject("acme",
            notFound(),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.5.7</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>acme</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <packaging>war</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void onlyApplicableModulesAreMarked() {
        rewriteRun(
          mavenProject("app-a",
            found(),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.4.13</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>app-a</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          ),
          mavenProject("app-b",
            srcMainJava(
              java(
                """
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  class OtherApplication {}
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.4.13</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>app-b</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <packaging>war</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void gradleJarProject() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("project",
            found(),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter:2.4.13'
                }
                """
            )
          )
        );
    }

    @Test
    void gradleWarProject() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("project",
            notFound(),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java'
                    id 'war'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter:2.4.13'
                }
                """
            )
          )
        );
    }
}

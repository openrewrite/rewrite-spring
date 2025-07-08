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
package org.openrewrite.java.spring.cloud2022;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class MigrateProjectTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.spring")
            .build()
            .activateRecipes("org.openrewrite.java.spring.cloud2022.MigrateCloudSleuthToMicrometerTracing"))
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "spring-cloud-sleuth-api-3.*"));
    }

    @Test
    void migrateSleuthStarter() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-starter-sleuth</artifactId>
                            <version>3.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(after -> {
                  Matcher matcher = Pattern.compile("            <version>(.*)</version>").matcher(after);
                  assertThat(matcher.find()).describedAs(after).isTrue();
                  String micrometerVersion = matcher.group(1);
                  return """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>io.micrometer</groupId>
                            <artifactId>micrometer-tracing-bridge-brave</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(micrometerVersion);
                })
            )
          )
        );
    }

    @Test
    void migrateProperties() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              //language=properties
              properties(
                """
                  spring.sleuth.baggage.correlation-enabled=true
                  """,
                """
                  management.tracing.baggage.correlation.enabled=true
                  """,
                s -> s.path("src/main/resources/application.properties")
              ),
              //language=yaml
              yaml(
                """
                  spring:
                      sleuth:
                          baggage:
                              correlation-enabled: true
                  """,
                """
                  management.tracing.baggage.correlation.enabled: true
                  """,
                s -> s.path("src/main/resources/application.yml")
              )
            )
          )
        );
    }

    @Test
    void migrateOldSleuthCore() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcMainJava(
              java(
                """
                  import org.springframework.cloud.sleuth.annotation.NewSpan;
                  class A {
                      @NewSpan
                      void m() {
                      }
                  }
                  """,
                """
                  import io.micrometer.tracing.annotation.NewSpan;
                  class A {
                      @NewSpan
                      void m() {
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-sleuth-core</artifactId>
                            <version>2.2.8.RELEASE</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(after -> {
                  Matcher matcher = Pattern.compile("            <version>(.*)</version>").matcher(after);
                  assertThat(matcher.find()).describedAs(after).isTrue();
                  String micrometerVersion = matcher.group(1);
                  assertThat(matcher.find()).describedAs(after).isTrue();
                  String springBootVersion = matcher.group(1);
                  return """
                        <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>explicit-deps-app</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                        <name>explicit-deps-app</name>
                        <description>explicit-deps-app</description>
                        <properties>
                            <java.version>17</java.version>
                            <maven.compiler.source>17</maven.compiler.source>
                            <maven.compiler.target>17</maven.compiler.target>
                        </properties>
                        <dependencies>
                            <dependency>
                                <groupId>io.micrometer</groupId>
                                <artifactId>micrometer-tracing</artifactId>
                                <version>%1$s</version>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-actuator</artifactId>
                                <version>%2$s</version>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-aop</artifactId>
                                <version>%2$s</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(micrometerVersion, springBootVersion);
              })
            )
          )
        );
    }
}

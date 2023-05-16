/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.cloud2022;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.xml.tree.Xml;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

public class MigrateProjectTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.spring.cloud2022.MigrateCloudSleuthToMicrometerTracing"))
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "spring-cloud-sleuth-api-3.*"));
    }

    Consumer<SourceSpec<Xml.Document>> withDynamicMicrometerVersion(@Language("xml") String fmt) {
        return spec -> spec.after(after -> {
            Matcher matcher = Pattern.compile(
                "<groupId>io.micrometer</groupId>\\s+<artifactId>[a-zA-Z-_.]+</artifactId>\\s+<version>(.*)</version>",
                Pattern.MULTILINE)
              .matcher(after);
            assertThat(matcher.find()).isTrue();
            String version = matcher.group(1);
            //language=xml
            return fmt.formatted(version);
        });
    }


    @Test
    void migrateSleuthStarter() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              //language=xml
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
              withDynamicMicrometerVersion("""
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
                """)
            )
          )
        );
    }

    @Test
    void migrateProperties() {
        rewriteRun(
          mavenProject("project",
            //language=properties
            properties(
              """
                spring.sleuth.baggage.correlation-enabled=true
                """,
              """
                management.tracing.baggage.correlation.enabled=true
                """
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
                """
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
              withDynamicMicrometerVersion("""
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
                            <version>%s</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-actuator</artifactId>
                            <version>3.0.6</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-aop</artifactId>
                            <version>3.0.6</version>
                        </dependency>
                    </dependencies>
                </project>
                """)
            )
          )
        );
    }
}

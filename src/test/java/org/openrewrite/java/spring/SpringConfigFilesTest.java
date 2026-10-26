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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.gradle.marker.GradlePluginDescriptor;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot2.MigrateArtemisProperties;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.RewriteTest.toRecipe;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringConfigFilesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeSpringPropertyKey("server.servlet-path", "server.servlet.path", null));
    }

    @DocumentExample
    @Test
    void changesFileImportedByApplicationYaml() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring:
                  config:
                    import: optional:file:./config/extra.properties
                """,
              spec -> spec.path("application.yml")
            )
          ),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            """
              server.servlet.path=/tmp/my-server-path
              """,
            spec -> spec.path("config/extra.properties")
          )
        );
    }

    @Test
    void leavesFileNothingPointsAtAlone() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring:
                  application:
                    name: demo
                """,
              spec -> spec.path("application.yml")
            )
          ),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            spec -> spec.path("config/extra.properties")
          )
        );
    }

    @Test
    void neverChangesWorkflowOrComposeFileThatPointsAtSpringConfiguration() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("name", "renamed", null, null, null)),
          //language=yaml
          yaml(
            """
              name: CI
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: java -jar app.jar --spring.config.location=file:./ci/
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          ),
          //language=yaml
          yaml(
            """
              name: demo
              services:
                app:
                  image: demo
                  environment:
                    SPRING_CONFIG_IMPORT: optional:file:./docker/extra.yml
              """,
            spec -> spec.path("compose.yaml")
          ),
          //language=yaml
          yaml(
            """
              name: ci
              """,
            """
              name: renamed
              """,
            spec -> spec.path("ci/application.yml")
          ),
          //language=yaml
          yaml(
            """
              name: docker
              """,
            """
              name: renamed
              """,
            spec -> spec.path("docker/extra.yml")
          )
        );
    }

    @Test
    void changesFilesNamedByPropertySourceAnnotations() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-context-6", "spring-test-6")),
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.PropertySource;

              @Configuration
              @PropertySource({"classpath:application.properties", "file:${CONFIG_DIR}/extra.properties"})
              class AppConfig {
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;

              import org.springframework.test.context.TestPropertySource;

              @TestPropertySource(locations = "it.properties")
              class AppIT {
              }
              """
          ),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            """
              server.servlet.path=/tmp/my-server-path
              """,
            spec -> spec.path("deploy/extra.properties")
          ),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            """
              server.servlet.path=/tmp/my-server-path
              """,
            spec -> spec.path("src/it/resources/com/example/it.properties")
          )
        );
    }

    @Test
    void changesFileCopiedToDockerfileAdditionalLocation() {
        rewriteRun(
          text(
            """
              FROM eclipse-temurin:21-jre
              WORKDIR /app
              COPY target/app.jar app.jar
              COPY deploy/ config/
              ENV SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/
              ENTRYPOINT ["java", "-jar", "app.jar"]
              """,
            spec -> spec.path("Dockerfile")
          ),
          //language=yaml
          yaml(
            """
              server:
                servlet-path: /tmp/my-server-path
              """,
            """
              server:
                servlet:
                  path: /tmp/my-server-path
              """,
            spec -> spec.path("deploy/application-docker.yml")
          ),
          //language=yaml
          yaml(
            """
              server:
                servlet-path: /tmp/my-server-path
              """,
            spec -> spec.path("deploy/values.yml")
          )
        );
    }

    @Test
    void changesFileNamedByKubernetesContainerEnvironment() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              apiVersion: apps/v1
              kind: Deployment
              metadata:
                name: demo
              spec:
                template:
                  spec:
                    containers:
                      - name: app
                        image: demo
                        env:
                          - name: SPRING_CONFIG_IMPORT
                            value: optional:file:./config/extra.yml
              """,
            spec -> spec.path("k8s/deployment.yml")
          ),
          //language=yaml
          yaml(
            """
              server:
                servlet-path: /tmp/my-server-path
              """,
            """
              server:
                servlet:
                  path: /tmp/my-server-path
              """,
            spec -> spec.path("config/extra.yml")
          )
        );
    }

    @Test
    void changesFilesNamedByArgumentsInScriptsAndBuilds() {
        rewriteRun(
          text(
            """
              #!/bin/sh
              exec java -jar app.jar --spring.config.additional-location=file:./conf/
              """,
            spec -> spec.path("bin/run.sh")
          ),
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <configuration>
                                    <jvmArguments>-Dspring.config.location=file:./local/</jvmArguments>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            ),
            //language=yaml
            yaml(
              """
                server:
                  servlet-path: /tmp/my-server-path
                """,
              """
                server:
                  servlet:
                    path: /tmp/my-server-path
                """,
              spec -> spec.path("local/application.yml")
            )
          ),
          //language=yaml
          yaml(
            """
              server:
                servlet-path: /tmp/my-server-path
              """,
            """
              server:
                servlet:
                  path: /tmp/my-server-path
              """,
            spec -> spec.path("conf/application-local.yml")
          )
        );
    }

    @Test
    void resolvesEachImportedLocation() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring:
                  config:
                    import:
                      - classpath:shared/common.yml
                      - optional:configtree:/etc/config/
                      - "${EXTRA_CONFIG}"
                """,
              spec -> spec.path("application.yml")
            )
          ),
          //language=yaml
          yaml(
            """
              server:
                servlet-path: /tmp/my-server-path
              """,
            """
              server:
                servlet:
                  path: /tmp/my-server-path
              """,
            spec -> spec.path("conf/shared/common.yml")
          ),
          //language=yaml
          yaml(
            """
              server:
                servlet-path: /tmp/my-server-path
              """,
            spec -> spec.path("conf/other.yml")
          )
        );
    }

    @Test
    void changesApplicationFilesSpringBootLoadsFromWorkingDirectoryOfModule() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.3.4</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0</version>
                </project>
                """
            ),
            //language=properties
            properties(
              """
                server.servlet-path=/tmp/my-server-path
                """,
              """
                server.servlet.path=/tmp/my-server-path
                """,
              spec -> spec.path("application.properties")
            ),
            //language=properties
            properties(
              """
                server.servlet-path=/tmp/my-server-path
                """,
              """
                server.servlet.path=/tmp/my-server-path
                """,
              spec -> spec.path("config/application-prod.properties")
            ),
            //language=properties
            properties(
              """
                server.servlet-path=/tmp/my-server-path
                """,
              """
                server.servlet.path=/tmp/my-server-path
                """,
              spec -> spec.path("config/eu/bootstrap.properties")
            ),
            //language=properties
            properties(
              """
                server.servlet-path=/tmp/my-server-path
                """,
              spec -> spec.path("config/other.properties")
            ),
            //language=properties
            properties(
              """
                server.servlet-path=/tmp/my-server-path
                """,
              spec -> spec.path("scripts/application.properties")
            )
          ),
          mavenProject("library",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>library</artifactId>
                    <version>1.0</version>
                </project>
                """
            ),
            //language=properties
            properties(
              """
                server.servlet-path=/tmp/my-server-path
                """,
              spec -> spec.path("config/application.properties")
            )
          )
        );
    }

    @Test
    void changesApplicationFileInConfigDirectoryOfSpringBootGradleProject() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'org.springframework.boot' version '3.3.4'
              }
              """,
            spec -> spec.markers(GradleProject.builder()
              .plugins(List.of(new GradlePluginDescriptor(
                "org.springframework.boot.gradle.plugin.SpringBootPlugin", "org.springframework.boot")))
              .build())
          ),
          //language=yaml
          yaml(
            """
              server:
                servlet-path: /tmp/my-server-path
              """,
            """
              server:
                servlet:
                  path: /tmp/my-server-path
              """,
            spec -> spec.path("config/application.yml")
          )
        );
    }

    @Test
    void everyPropertyRecipeChangesDiscoveredFiles() {
        rewriteRun(
          spec -> spec.recipes(
            new ChangeSpringPropertyValue("server.port", "8081", null, null, null),
            new DeleteSpringProperty("server.servlet-path"),
            new CommentOutSpringPropertyKey("server.tomcat.max-threads", "Removed.", null),
            new MigrateArtemisProperties()
          ),
          srcMainResources(
            //language=properties
            properties(
              """
                spring.config.import=optional:file:./config/extra.properties
                """,
              spec -> spec.path("application.properties")
            )
          ),
          //language=properties
          properties(
            """
              server.port=8080
              server.servlet-path=/api
              spring.artemis.host=broker
              spring.artemis.port=61617
              server.tomcat.max-threads=10
              """,
            """
              server.port=8081
              spring.artemis.broker-url=tcp://broker:61617
              # Removed.
              # server.tomcat.max-threads=10
              """,
            spec -> spec.path("config/extra.properties")
          )
        );
    }

    @Test
    void withoutScanningChangesOnlyFilesInSourceSets() {
        rewriteRun(
          spec -> spec.recipe(toRecipe(() ->
            new ChangeSpringPropertyKey("server.servlet-path", "server.servlet.path", null).getVisitor())),
          srcMainResources(
            //language=properties
            properties(
              """
                spring.config.import=optional:file:./config/extra.properties
                server.servlet-path=/tmp/my-server-path
                """,
              """
                spring.config.import=optional:file:./config/extra.properties
                server.servlet.path=/tmp/my-server-path
                """,
              spec -> spec.path("application.properties")
            )
          ),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            spec -> spec.path("config/extra.properties")
          )
        );
    }
}

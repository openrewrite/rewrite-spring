/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot2.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

/**
 * @author Alex Boyko
 */
@SuppressWarnings("UnusedProperty")
class IntegrationSchedulerPoolRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new IntegrationSchedulerPoolRecipe())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot", "spring-boot-autoconfigure"));
    }

    @Test
    void wrongBootVersion() {
        rewriteRun(
          mavenProject("sample",
            pomXml(
              //language=xml
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.5.7</version>
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
                    
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void noIntegrationDependency() {
        rewriteRun(
          mavenProject("sample",
            //language=xml
            pomXml(
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
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
                    
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void noSchedulerProperty() {
        rewriteRun(
          mavenProject("sample",
            pomXml(
              //language=xml
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
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
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-integration</artifactId>
                        </dependency>
                    
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              properties(
                "server.port=5674",
                spec -> spec.path("application.properties")
              ),
              //language=java
              java(
                """
                  package demo;

                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  public class ExampleApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(ExampleApplication.class, args);
                      }
                  }
                  """,
                """
                  package demo;
                                    
                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;
                                    
                  // TODO: Scheduler thread pool size for Spring Integration either in properties or config server
                  @SpringBootApplication
                  public class ExampleApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(ExampleApplication.class, args);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void schedulerPropertyPresent() {
        rewriteRun(
          mavenProject("sample",
            pomXml(
              //language=xml
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
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
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-integration</artifactId>
                        </dependency>
                    
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  package demo;
                                    
                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;
                                    
                  @SpringBootApplication
                  public class ExampleApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(ExampleApplication.class, args);
                      }
                  }
                  """
              )
            ),
            srcMainResources(
              //language=properties
              properties(
                """
                      server.port=6473
                      spring.task.scheduling.pool.size=4
                      spring.application.name=demo
                  """,
                """
                      server.port=6473
                      # TODO: Consider Scheduler thread pool size for Spring Integration
                      spring.task.scheduling.pool.size=4
                      spring.application.name=demo
                  """,
                spec -> spec.path("application.properties")
              )
            )
          )
        );
    }

    @Test
    void noSchedulerPropertyYaml() {
        rewriteRun(
          mavenProject("sample",
            pomXml(
              //language=xml
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
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
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-integration</artifactId>
                        </dependency>
                    
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainResources(
              yaml(
                //language=yaml
                """
                  server:
                    port: 4563
                  """,
                spec -> spec.path("application.yaml")
              )
            ),
            srcMainJava(
              //language=java
              java(
                """
                  package demo;
                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;
                  @SpringBootApplication
                  public class ExampleApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(ExampleApplication.class, args);
                      }
                  }
                  """,
                """
                  package demo;
                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;
                  // TODO: Scheduler thread pool size for Spring Integration either in properties or config server
                  @SpringBootApplication
                  public class ExampleApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(ExampleApplication.class, args);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void schedulerPropertyPresentYaml() {
        rewriteRun(
          mavenProject("sample",
            pomXml(
              //language=xml
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
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
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-integration</artifactId>
                        </dependency>
                    
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  package demo;
                                    
                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;
                                    
                  @SpringBootApplication
                  public class ExampleApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(ExampleApplication.class, args);
                      }
                  }
                  """
              )
            ),
            srcMainResources(
              yaml(
                """
                      server:
                        port: 4563
                      spring:
                        task:
                          scheduling:
                            foo: bar
                            pool:
                              size: 6
                            bar: foo
                        application:
                          name: demo
                  """,
                """
                      server:
                        port: 4563
                      spring:
                        task:
                          scheduling:
                            foo: bar
                            pool:
                      # TODO: Consider Scheduler thread pool size for Spring Integration
                              size: 6
                            bar: foo
                        application:
                          name: demo
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }
}

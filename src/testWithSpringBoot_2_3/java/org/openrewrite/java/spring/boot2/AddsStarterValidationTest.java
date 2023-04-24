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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class AddsStarterValidationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_3")
          ).parser(
            JavaParser.fromJavaVersion()
              .logCompilationWarningsAndErrors(true)
              .classpathFromResources(new InMemoryExecutionContext(), "validation-api"));
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/306")
    @Test
    void shouldAddValidationStarterIfBVIsUsed() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=Java
              java("""
                import javax.validation.constraints.NotNull;
                                    
                class Foo {
                    @NotNull
                    String bar = "";
                }
                """),
              //language=XML
              pomXml("""
                  <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter-parent</artifactId>
                          <version>2.2.13.RELEASE</version>
                          <relativePath/> <!-- lookup parent from repository -->
                      </parent>
                      <groupId>com.example</groupId>
                      <artifactId>acme</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.boot</groupId>
                              <artifactId>spring-boot-starter-web</artifactId>
                          </dependency>
                          <dependency>
                              <groupId>javax.validation</groupId>
                              <artifactId>validation-api</artifactId>
                          </dependency>
                      </dependencies>
                  </project>
                  """,
                """
                  <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter-parent</artifactId>
                          <version>2.3.12.RELEASE</version>
                          <relativePath/> <!-- lookup parent from repository -->
                      </parent>
                      <groupId>com.example</groupId>
                      <artifactId>acme</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.boot</groupId>
                              <artifactId>spring-boot-starter-web</artifactId>
                          </dependency>
                          <dependency>
                              <groupId>javax.validation</groupId>
                              <artifactId>validation-api</artifactId>
                          </dependency>
                          <dependency>
                              <groupId>org.springframework.boot</groupId>
                              <artifactId>spring-boot-starter-validation</artifactId>
                          </dependency>
                      </dependencies>
                  </project>
                  """
              )
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/306")
    @Test
    void shouldNotAddValidationStarterIfBVIsNotUsed() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=Java
              java("""
                class Foo {
                    String bar = "";
                }
                """),
              //language=XML
              pomXml("""
                  <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter-parent</artifactId>
                          <version>2.2.13.RELEASE</version>
                          <relativePath/> <!-- lookup parent from repository -->
                      </parent>
                      <groupId>com.example</groupId>
                      <artifactId>acme</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.boot</groupId>
                              <artifactId>spring-boot-starter-web</artifactId>
                          </dependency>
                      </dependencies>
                  </project>
                  """,
                """
                  <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter-parent</artifactId>
                          <version>2.3.12.RELEASE</version>
                          <relativePath/> <!-- lookup parent from repository -->
                      </parent>
                      <groupId>com.example</groupId>
                      <artifactId>acme</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.boot</groupId>
                              <artifactId>spring-boot-starter-web</artifactId>
                          </dependency>
                      </dependencies>
                  </project>
                  """
              )
            )
          )
        );
    }

}

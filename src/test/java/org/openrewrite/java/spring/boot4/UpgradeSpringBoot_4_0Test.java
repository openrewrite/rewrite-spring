/*
 * Copyright 2025 the original author or authors.
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
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringBoot_4_0Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0");
    }

    @Test
    void flywayStarterOmitsVersionWhenManagedByParent() {
        rewriteRun(
          mavenProject("parent",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.13</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>${revision}</version>
                    <packaging>pom</packaging>
                    <properties>
                        <revision>1.0.0-SNAPSHOT</revision>
                    </properties>
                    <modules>
                        <module>app</module>
                    </modules>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).containsPattern("<version>4\\.0\\.\\d+</version>");
                  return pom;
              })
            ),
            mavenProject("app",
              srcMainJava(
                //language=java
                java(
                  """
                    package com.example;
                    class App {
                        String s = "";
                    }
                    """
                )
              ),
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>com.example</groupId>
                          <artifactId>parent</artifactId>
                          <version>${revision}</version>
                      </parent>
                      <artifactId>app</artifactId>
                      <dependencies>
                          <dependency>
                              <groupId>org.flywaydb</groupId>
                              <artifactId>flyway-database-postgresql</artifactId>
                          </dependency>
                      </dependencies>
                  </project>
                  """,
                spec -> spec.after(pom -> {
                    assertThat(pom)
                      .contains("<artifactId>spring-boot-starter-flyway</artifactId>")
                      .doesNotContainPattern("spring-boot-starter-flyway</artifactId>\\s*<version>");
                    return pom;
                })
              )
            )
          )
        );
    }
}

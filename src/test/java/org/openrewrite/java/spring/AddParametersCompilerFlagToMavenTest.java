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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class AddParametersCompilerFlagToMavenTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddParametersCompilerFlagToMaven());
    }

    @DocumentExample
    @Test
    void addsCompilerParametersProperty() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                  </properties>
              </project>
              """
          )
        );
    }

    @Test
    void addsPropertyToExistingProperties() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.release>17</maven.compiler.release>
                  </properties>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <maven.compiler.release>17</maven.compiler.release>
                  </properties>
              </project>
              """
          )
        );
    }

    @Test
    void addsPropertyWhenPluginDeclaredWithoutParametersConfig() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-compiler-plugin</artifactId>
                              <configuration>
                                  <compilerArgs>
                                      <compilerArg>-Xlint:all</compilerArg>
                                  </compilerArgs>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                  </properties>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-compiler-plugin</artifactId>
                              <configuration>
                                  <compilerArgs>
                                      <compilerArg>-Xlint:all</compilerArg>
                                  </compilerArgs>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void noChangeWhenCompilerArgsContainParameters() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <build>
                      <plugins>
                          <plugin>
                              <artifactId>maven-compiler-plugin</artifactId>
                              <configuration>
                                  <compilerArgs>
                                      <arg>-parameters</arg>
                                  </compilerArgs>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void noChangeWhenParametersOptionConfigured() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-compiler-plugin</artifactId>
                              <configuration>
                                  <parameters>true</parameters>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void noChangeWhenPropertyAlreadyDecided() {
        // An explicit "false" is a decision to respect, not a missing flag
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>false</maven.compiler.parameters>
                  </properties>
              </project>
              """
          )
        );
    }

    @Test
    void noChangeWhenCompilerArgumentDecidesParameters() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <build>
                      <plugins>
                          <plugin>
                              <artifactId>maven-compiler-plugin</artifactId>
                              <configuration>
                                  <compilerArgument>-parameters</compilerArgument>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void noChangeWhenPluginManagementDecidesParameters() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <build>
                      <pluginManagement>
                          <plugins>
                              <plugin>
                                  <groupId>org.apache.maven.plugins</groupId>
                                  <artifactId>maven-compiler-plugin</artifactId>
                                  <configuration>
                                      <parameters>true</parameters>
                                  </configuration>
                              </plugin>
                          </plugins>
                      </pluginManagement>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void doesNotModifyWhenInheritingFromSpringBootStarterParent() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>3.3.0</version>
                      <relativePath/>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
              </project>
              """
          )
        );
    }

    @Test
    void addsPropertyWhenSpringBootVersionPropertyIsDefinedWithoutStarterParent() {
        // Projects importing the Spring Boot BOM often declare a spring-boot.version property;
        // that alone does not retain parameter names, so the flag must still be added.
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <spring-boot.version>3.3.0</spring-boot.version>
                  </properties>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <spring-boot.version>3.3.0</spring-boot.version>
                  </properties>
              </project>
              """
          )
        );
    }

    @Test
    void addsJavaParametersToKotlinPluginWithNoConfig() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.jetbrains.kotlin</groupId>
                              <artifactId>kotlin-maven-plugin</artifactId>
                              <version>1.9.25</version>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                  </properties>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.jetbrains.kotlin</groupId>
                              <artifactId>kotlin-maven-plugin</artifactId>
                              <version>1.9.25</version>
                              <configuration>
                                  <javaParameters>true</javaParameters>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void addsJavaParametersToKotlinPluginWithExistingConfig() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.jetbrains.kotlin</groupId>
                              <artifactId>kotlin-maven-plugin</artifactId>
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
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                  </properties>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.jetbrains.kotlin</groupId>
                              <artifactId>kotlin-maven-plugin</artifactId>
                              <configuration>
                                  <args>
                                      <arg>-Xjsr305=strict</arg>
                                  </args>
                                  <javaParameters>true</javaParameters>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void noChangeToKotlinPluginWhenJavaParametersArgPresent() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                  </properties>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.jetbrains.kotlin</groupId>
                              <artifactId>kotlin-maven-plugin</artifactId>
                              <configuration>
                                  <args>
                                      <arg>-java-parameters</arg>
                                  </args>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void respectsExplicitKotlinJavaParametersFalse() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                  </properties>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.jetbrains.kotlin</groupId>
                              <artifactId>kotlin-maven-plugin</artifactId>
                              <configuration>
                                  <javaParameters>false</javaParameters>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void multiModuleWithStarterParentIsUntouched() {
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
                        <version>3.3.0</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>child</module>
                    </modules>
                </project>
                """
            ),
            mavenProject("child",
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>com.example</groupId>
                          <artifactId>parent</artifactId>
                          <version>1.0-SNAPSHOT</version>
                      </parent>
                      <artifactId>child</artifactId>
                  </project>
                  """
              )
            )
          )
        );
    }

    @Test
    void childModuleInheritsFlagFromInProjectParent() {
        rewriteRun(
          mavenProject("parent",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>child</module>
                    </modules>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>child</module>
                    </modules>
                    <properties>
                        <maven.compiler.parameters>true</maven.compiler.parameters>
                    </properties>
                </project>
                """
            ),
            mavenProject("child",
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <parent>
                          <groupId>com.example</groupId>
                          <artifactId>parent</artifactId>
                          <version>1.0-SNAPSHOT</version>
                      </parent>
                      <artifactId>child</artifactId>
                  </project>
                  """
              )
            )
          )
        );
    }

    @Test
    void compositeRecipeAppliesToSpringProjects() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.AddParametersCompilerFlag"),
          mavenProject("demo",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-context</artifactId>
                            <version>6.1.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <properties>
                        <maven.compiler.parameters>true</maven.compiler.parameters>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-context</artifactId>
                            <version>6.1.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void compositeRecipeSkipsNonSpringProjects() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.AddParametersCompilerFlag"),
          mavenProject("demo",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                </project>
                """
            )
          )
        );
    }
}

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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.settingsGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

@Issue("https://github.com/openrewrite/rewrite-spring/issues/145")
class UpgradeSpockToGroovy3Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpockToGroovy3");
    }

    @DocumentExample
    @Test
    void upgradeSpockWhenUsingGroovy3Maven() {
        rewriteRun(
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
                    <groupId>org.codehaus.groovy</groupId>
                    <artifactId>groovy</artifactId>
                    <version>3.0.9</version>
                  </dependency>
                  <dependency>
                    <groupId>org.spockframework</groupId>
                    <artifactId>spock-core</artifactId>
                    <version>1.3-groovy-2.5</version>
                    <scope>test</scope>
                  </dependency>
                  <dependency>
                    <groupId>org.spockframework</groupId>
                    <artifactId>spock-spring</artifactId>
                    <version>1.3-groovy-2.5</version>
                    <scope>test</scope>
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
                <dependencies>
                  <dependency>
                    <groupId>org.codehaus.groovy</groupId>
                    <artifactId>groovy</artifactId>
                    <version>3.0.9</version>
                  </dependency>
                  <dependency>
                    <groupId>org.spockframework</groupId>
                    <artifactId>spock-core</artifactId>
                    <version>2.0-groovy-3.0</version>
                    <scope>test</scope>
                  </dependency>
                  <dependency>
                    <groupId>org.spockframework</groupId>
                    <artifactId>spock-spring</artifactId>
                    <version>2.0-groovy-3.0</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void upgradeSpockWhenUsingGroovy3Gradle() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          //language=groovy
          buildGradle(
            """
              plugins {
                id 'groovy'
              }

              repositories {
                mavenCentral()
              }

              dependencies {
                implementation 'org.codehaus.groovy:groovy:3.0.9'
                testImplementation 'org.spockframework:spock-core:1.3-groovy-2.5'
                testImplementation 'org.spockframework:spock-spring:1.3-groovy-2.5'
              }

              tasks.withType(Test).configureEach {
                useJUnitPlatform()
              }
              """,
            """
              plugins {
                id 'groovy'
              }

              repositories {
                mavenCentral()
              }

              dependencies {
                implementation 'org.codehaus.groovy:groovy:3.0.9'
                testImplementation 'org.spockframework:spock-core:2.0-groovy-3.0'
                testImplementation 'org.spockframework:spock-spring:2.0-groovy-3.0'
              }

              tasks.withType(Test).configureEach {
                useJUnitPlatform()
              }
              """
          )
        );
    }

    @Test
    void groovyAllAlreadyOnGroovy3KeepsItsVersion() {
        rewriteRun(
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
                    <groupId>org.codehaus.groovy</groupId>
                    <artifactId>groovy-all</artifactId>
                    <version>3.0.9</version>
                    <type>pom</type>
                  </dependency>
                  <dependency>
                    <groupId>org.spockframework</groupId>
                    <artifactId>spock-core</artifactId>
                    <version>1.3-groovy-2.5</version>
                    <scope>test</scope>
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
                <dependencies>
                  <dependency>
                    <groupId>org.codehaus.groovy</groupId>
                    <artifactId>groovy-all</artifactId>
                    <version>3.0.9</version>
                    <type>pom</type>
                    <exclusions>
                      <exclusion>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-testng</artifactId>
                      </exclusion>
                    </exclusions>
                  </dependency>
                  <dependency>
                    <groupId>org.spockframework</groupId>
                    <artifactId>spock-core</artifactId>
                    <version>2.0-groovy-3.0</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void groovyAllInParentAndSpockInModule() {
        rewriteRun(
          mavenProject("parent",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>child</module>
                  </modules>
                  <dependencies>
                    <dependency>
                      <groupId>org.codehaus.groovy</groupId>
                      <artifactId>groovy-all</artifactId>
                      <version>3.0.9</version>
                      <type>pom</type>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>child</module>
                  </modules>
                  <dependencies>
                    <dependency>
                      <groupId>org.codehaus.groovy</groupId>
                      <artifactId>groovy-all</artifactId>
                      <version>3.0.9</version>
                      <type>pom</type>
                      <exclusions>
                        <exclusion>
                          <groupId>org.codehaus.groovy</groupId>
                          <artifactId>groovy-testng</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
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
                      <version>0.0.1-SNAPSHOT</version>
                    </parent>
                    <artifactId>child</artifactId>
                    <dependencies>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.3-groovy-2.5</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """,
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                      <groupId>com.example</groupId>
                      <artifactId>parent</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                    </parent>
                    <artifactId>child</artifactId>
                    <dependencies>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>2.0-groovy-3.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            )
          )
        );
    }

    @Test
    void groovyVersionInheritedFromSpringBootFollowsTheParent() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_5"),
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <parent>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-parent</artifactId>
                  <version>2.2.13.RELEASE</version>
                  <relativePath/>
                </parent>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>org.codehaus.groovy</groupId>
                    <artifactId>groovy-all</artifactId>
                    <version>${groovy.version}</version>
                    <type>pom</type>
                  </dependency>
                  <dependency>
                    <groupId>org.spockframework</groupId>
                    <artifactId>spock-core</artifactId>
                    <version>1.3-groovy-2.5</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """,
            spec -> spec.after(actual -> {
                assertThat(actual)
                  .contains("<version>${groovy.version}</version>")
                  .contains("<version>2.0-groovy-3.0</version>")
                  .contains("<artifactId>groovy-testng</artifactId>")
                  .doesNotContain("<groovy.version>");
                return actual;
            })
          )
        );
    }

    @Nested
    class KeepSpecificationsRunningOnGroovy3 {

        @Test
        void gradleMovesToJUnitPlatform() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              //language=groovy
              buildGradle(
                """
                  plugins {
                    id 'groovy'
                  }

                  repositories {
                    mavenCentral()
                  }

                  dependencies {
                    implementation 'org.codehaus.groovy:groovy:3.0.9'
                    testImplementation 'org.spockframework:spock-core:1.3-groovy-2.5'
                  }
                  """,
                """
                  plugins {
                    id 'groovy'
                  }

                  repositories {
                    mavenCentral()
                  }

                  dependencies {
                    implementation 'org.codehaus.groovy:groovy:3.0.9'
                    testImplementation 'org.spockframework:spock-core:2.0-groovy-3.0'
                  }
                  tasks.withType(Test).configureEach {
                      useJUnitPlatform()
                  }
                  """
              )
            );
        }

        @Test
        void gradleModuleStayingOnSpock1KeepsItsTestRunner() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              settingsGradle(
                """
                  rootProject.name = 'demo'
                  include 'moving', 'staying'
                  """
              ),
              mavenProject("moving",
                //language=groovy
                buildGradle(
                  """
                    plugins {
                      id 'groovy'
                    }

                    repositories {
                      mavenCentral()
                    }

                    dependencies {
                      implementation 'org.codehaus.groovy:groovy:3.0.9'
                      testImplementation 'org.spockframework:spock-core:1.3-groovy-2.5'
                    }
                    """,
                  spec -> spec.after(actual -> {
                      assertThat(actual).contains("useJUnitPlatform()", "spock-core:2.0-groovy-3.0");
                      return actual;
                  })
                )
              ),
              mavenProject("staying",
                //language=groovy
                buildGradle(
                  """
                    plugins {
                      id 'groovy'
                    }

                    repositories {
                      mavenCentral()
                    }

                    dependencies {
                      implementation 'org.codehaus.groovy:groovy:2.5.14'
                      testImplementation 'org.spockframework:spock-core:1.3-groovy-2.5'
                    }
                    """
                )
              )
            );
        }

        @Test
        void surefireBelowJUnitPlatformSupport() {
            rewriteRun(
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
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>3.0.9</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.3-groovy-2.5</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <version>2.20</version>
                        </plugin>
                      </plugins>
                    </build>
                  </project>
                  """,
                spec -> spec.after(actual -> {
                    assertThat(actual)
                      .contains("<version>2.22.2</version>", "<version>2.0-groovy-3.0</version>")
                      .doesNotContain("<version>2.20</version>");
                    return actual;
                })
              )
            );
        }
    }

    @Nested
    class NoChange {

        @Test
        void whenUsingGroovy25() {
            rewriteRun(
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
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>2.5.14</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.3-groovy-2.5</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void whenAlreadyOnNewerSpock() {
            rewriteRun(
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
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>3.0.9</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>2.1-groovy-3.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void gradleAlreadyOnSpock2KeepsItsTestRunner() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              //language=groovy
              buildGradle(
                """
                  plugins {
                    id 'groovy'
                  }

                  repositories {
                    mavenCentral()
                  }

                  dependencies {
                    implementation 'org.codehaus.groovy:groovy:3.0.9'
                    testImplementation 'org.spockframework:spock-core:2.0-groovy-3.0'
                  }
                  """
              )
            );
        }

        @Test
        void gradleVersionInGradlePropertiesOnGroovy3() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject("demo",
                //language=properties
                properties(
                  """
                    spockVersion=1.3-groovy-2.5
                    """,
                  spec -> spec.path("gradle.properties")
                ),
                //language=groovy
                buildGradle(
                  """
                    plugins {
                      id 'groovy'
                    }

                    repositories {
                      mavenCentral()
                    }

                    dependencies {
                      implementation 'org.codehaus.groovy:groovy:3.0.9'
                      testImplementation "org.spockframework:spock-core:${spockVersion}"
                    }
                    """
                )
              )
            );
        }

        @Test
        void surefireAlreadyOnSpock2() {
            rewriteRun(
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
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>3.0.9</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>2.1-groovy-3.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <version>2.20</version>
                        </plugin>
                      </plugins>
                    </build>
                  </project>
                  """
              )
            );
        }

        @Test
        void surefireWhenSpock1StaysOnGroovy25() {
            rewriteRun(
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
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>2.5.14</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.3-groovy-2.5</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <version>2.20</version>
                        </plugin>
                      </plugins>
                    </build>
                  </project>
                  """
              )
            );
        }

        @Test
        void groovyAllOnGroovy3WithoutSpock() {
            rewriteRun(
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
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>3.0.9</version>
                        <type>pom</type>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void groovyAllWithNewerSpock() {
            rewriteRun(
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
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>3.0.9</version>
                        <type>pom</type>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>2.1-groovy-3.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }
    }
}

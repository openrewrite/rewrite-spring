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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.settingsGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

@Issue("https://github.com/openrewrite/rewrite-spring/issues/145")
class UpgradeSpockToGroovy3Test implements RewriteTest {
    // `3.0.x` selects the newest Groovy 3 release, so the tests read the patch version from the result
    private static final Pattern GROOVY_3 = Pattern.compile("\\b3\\.0\\.\\d+\\b");

    private static String groovy3In(String actual) {
        Matcher matcher = GROOVY_3.matcher(actual);
        assertThat(matcher.find()).as("a Groovy 3 version in:%n%s", actual).isTrue();
        return matcher.group();
    }

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
    class PinnedGroovyAll {

        @Test
        void upgradeGroovyAllAndSpockTogether() {
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
                        <version>2.4.11</version>
                        <scope>test</scope>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.1-groovy-2.4-rc-2</version>
                        <scope>test</scope>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-spring</artifactId>
                        <version>1.1-groovy-2.4-rc-2</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """,
                spec -> spec.after(actual -> """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>%s</version>
                        <type>pom</type>
                        <scope>test</scope>
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
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-spring</artifactId>
                        <version>2.0-groovy-3.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """.formatted(groovy3In(actual)))
              )
            );
        }

        @Test
        void upgradeThroughSpringBoot27() {
            rewriteRun(
              // The JUnit 4 exclusions of the chain reach the JUnit 4 under `groovy-all` 3 in the next cycle
              spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7")
                .cycles(3).expectedCyclesThatMakeChanges(2),
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
                        <version>2.4.11</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.1-groovy-2.4-rc-2</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """,
                spec -> spec.after(actual -> {
                    assertThat(actual)
                      .contains("<version>2.7.18</version>")
                      .contains("<type>pom</type>")
                      .contains("<version>2.0-groovy-3.0</version>")
                      .doesNotContain("2.4.11", "groovy-2.4");
                    groovy3In(actual);
                    return actual;
                })
              )
            );
        }

        @Test
        void managedInParentPomThroughSpringBoot27() {
            rewriteRun(
              // A module's model keeps the parent it was parsed with, whose managed `groovy-all` is still a `jar`, so
              // updating that model reports the `pom` the module now declares as having no version, without failing
              spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7")
                .executionContext(new InMemoryExecutionContext()),
              mavenProject("parent",
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
                      <artifactId>parent</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <packaging>pom</packaging>
                      <modules>
                        <module>child</module>
                      </modules>
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-all</artifactId>
                            <version>2.4.11</version>
                          </dependency>
                          <dependency>
                            <groupId>org.spockframework</groupId>
                            <artifactId>spock-core</artifactId>
                            <version>1.1-groovy-2.4</version>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                    </project>
                    """,
                  spec -> spec.after(actual -> {
                      assertThat(actual)
                        .contains("<version>2.7.18</version>", "<type>pom</type>", "<version>2.0-groovy-3.0</version>")
                        .doesNotContain("2.4.11", "groovy-2.4", "~~");
                      return actual;
                  })
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
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-all</artifactId>
                          </dependency>
                          <dependency>
                            <groupId>org.spockframework</groupId>
                            <artifactId>spock-core</artifactId>
                            <scope>test</scope>
                          </dependency>
                        </dependencies>
                      </project>
                      """,
                    spec -> spec.after(actual -> {
                        assertThat(actual).contains("<type>pom</type>").doesNotContain("~~");
                        return actual;
                    })
                  )
                )
              )
            );
        }

        @Test
        void versionsInProperties() {
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
                      <groovy.version>2.4.11</groovy.version>
                      <spock.version>1.1-groovy-2.4</spock.version>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>${groovy.version}</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>${spock.version}</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """,
                spec -> spec.after(actual -> """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <properties>
                      <groovy.version>%s</groovy.version>
                      <spock.version>2.0-groovy-3.0</spock.version>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>${groovy.version}</version>
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
                        <version>${spock.version}</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """.formatted(groovy3In(actual)))
              )
            );
        }

        @Test
        void managedInParentPom() {
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
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-all</artifactId>
                            <version>2.4.11</version>
                          </dependency>
                          <dependency>
                            <groupId>org.spockframework</groupId>
                            <artifactId>spock-core</artifactId>
                            <version>1.1-groovy-2.4</version>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                    </project>
                    """,
                  spec -> spec.after(actual -> """
                    <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>parent</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <packaging>pom</packaging>
                      <modules>
                        <module>child</module>
                      </modules>
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-all</artifactId>
                            <version>%s</version>
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
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                    </project>
                    """.formatted(groovy3In(actual)))
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
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-all</artifactId>
                          </dependency>
                          <dependency>
                            <groupId>org.spockframework</groupId>
                            <artifactId>spock-core</artifactId>
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
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-all</artifactId>
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
        void versionPropertyInParentPom() {
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
                      <properties>
                        <groovy.version>2.4.11</groovy.version>
                        <spock.version>1.1-groovy-2.4</spock.version>
                      </properties>
                    </project>
                    """,
                  spec -> spec.after(actual -> """
                    <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>parent</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <packaging>pom</packaging>
                      <modules>
                        <module>child</module>
                      </modules>
                      <properties>
                        <groovy.version>%s</groovy.version>
                        <spock.version>2.0-groovy-3.0</spock.version>
                      </properties>
                    </project>
                    """.formatted(groovy3In(actual)))
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
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-all</artifactId>
                            <version>${groovy.version}</version>
                          </dependency>
                          <dependency>
                            <groupId>org.spockframework</groupId>
                            <artifactId>spock-core</artifactId>
                            <version>${spock.version}</version>
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
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-all</artifactId>
                            <version>${groovy.version}</version>
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
                            <version>${spock.version}</version>
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
        void groovyPinnedBesideGroovyAll() {
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
                        <version>2.4.11</version>
                      </dependency>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>2.4.11</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.1-groovy-2.4</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """,
                spec -> spec.after(actual -> """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>%1$s</version>
                      </dependency>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>%1$s</version>
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
                  """.formatted(groovy3In(actual)))
              )
            );
        }

        @Test
        void groovyAllAlreadyPublishedAsPom() {
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
                        <version>2.5.14</version>
                        <type>pom</type>
                        <exclusions>
                          <exclusion>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-swing</artifactId>
                          </exclusion>
                        </exclusions>
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
                spec -> spec.after(actual -> """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>%s</version>
                        <type>pom</type>
                        <exclusions>
                          <exclusion>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-swing</artifactId>
                          </exclusion>
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
                  """.formatted(groovy3In(actual)))
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
                        <artifactId>groovy-all</artifactId>
                        <version>2.4.11</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.1-groovy-2.4</version>
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
                    assertThat(actual).contains("<version>2.22.2</version>").doesNotContain("<version>2.20</version>");
                    return actual;
                })
              )
            );
        }

        @Test
        void gradle() {
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
                    testImplementation 'org.codehaus.groovy:groovy-all:2.4.11'
                    testImplementation 'org.spockframework:spock-core:1.1-groovy-2.4'
                  }

                  tasks.withType(Test).configureEach {
                    useJUnitPlatform()
                  }
                  """,
                spec -> spec.after(actual -> """
                  plugins {
                    id 'groovy'
                  }

                  repositories {
                    mavenCentral()
                  }

                  dependencies {
                    testImplementation 'org.codehaus.groovy:groovy-all:%s'
                    testImplementation 'org.spockframework:spock-core:2.0-groovy-3.0'
                  }

                  tasks.withType(Test).configureEach {
                    useJUnitPlatform()
                  }
                  """.formatted(groovy3In(actual)))
              )
            );
        }

        @Test
        void gradleSeveralDependenciesInOneDeclaration() {
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
                    testImplementation(
                      'org.codehaus.groovy:groovy-all:2.4.15',
                      'org.spockframework:spock-core:1.1-groovy-2.4'
                    )
                  }
                  """,
                spec -> spec.after(actual -> {
                    assertThat(actual)
                      .contains("spock-core:2.0-groovy-3.0", "useJUnitPlatform()")
                      .doesNotContain("groovy-all:2.4.15");
                    groovy3In(actual);
                    return actual;
                })
              )
            );
        }

        @Test
        void gradleModuleWithoutSpockKeepsItsTestRunner() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              settingsGradle(
                """
                  rootProject.name = 'demo'
                  include 'specs', 'plain'
                  """
              ),
              mavenProject("specs",
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
                      testImplementation 'org.codehaus.groovy:groovy-all:2.4.11'
                      testImplementation 'org.spockframework:spock-core:1.1-groovy-2.4'
                    }
                    """,
                  spec -> spec.after(actual -> {
                      assertThat(actual).contains("useJUnitPlatform()", "spock-core:2.0-groovy-3.0");
                      return actual;
                  })
                )
              ),
              mavenProject("plain",
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
                      testImplementation 'junit:junit:4.13.2'
                    }
                    """
                )
              )
            );
        }

        @Test
        void gradleVersionsInGradleProperties() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject("demo",
                //language=properties
                properties(
                  """
                    groovyVersion=2.4.11
                    spockVersion=1.1-groovy-2.4
                    """,
                  spec -> spec.path("gradle.properties").after(actual -> """
                    groovyVersion=%s
                    spockVersion=2.0-groovy-3.0
                    """.formatted(groovy3In(actual)))
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
                      testImplementation "org.codehaus.groovy:groovy-all:${groovyVersion}"
                      testImplementation "org.spockframework:spock-core:${spockVersion}"
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
                      testImplementation "org.codehaus.groovy:groovy-all:${groovyVersion}"
                      testImplementation "org.spockframework:spock-core:${spockVersion}"
                    }
                    tasks.withType(Test).configureEach {
                        useJUnitPlatform()
                    }
                    """
                )
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
        void groovyAllPinnedWithoutSpock() {
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
                        <version>2.4.11</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void groovyAllOnlyThroughSpock() {
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
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.1-groovy-2.4</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void groovyAllVersionInheritedFromSpringBoot() {
            rewriteRun(
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
                  """
              )
            );
        }

        @Test
        void spock2StaysOnGroovy25() {
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
                        <version>2.5.14</version>
                        <type>pom</type>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>2.1-groovy-2.5</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        /**
         * The upgrade cannot edit a version kept in a map, so neither moves. Putting the Spock 1.x that stays on the
         * JUnit Platform would stop its specifications from running.
         */
        @Test
        void gradleVersionsInAMap() {
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

                  ext.deps = [
                    spock: '1.3-groovy-2.5',
                    groovy: '2.5.6'
                  ]

                  dependencies {
                    testImplementation "org.spockframework:spock-core:${deps.spock}"
                    testImplementation "org.codehaus.groovy:groovy-all:${deps.groovy}"
                  }
                  """
              )
            );
        }

        /**
         * A root project without the test configurations keeps the upgrade from seeing `groovy-all`, while a subproject
         * still shows it the Spock version, so without a gate Spock would move and leave Groovy behind.
         */
        @Test
        void groovyAllOnlyInRootSubprojectsBlock() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              settingsGradle(
                """
                  rootProject.name = 'demo'
                  include 'app'
                  """
              ),
              //language=properties
              properties(
                """
                  spockVersion=1.2-groovy-2.5
                  groovyVersion=2.5.5
                  """,
                spec -> spec.path("gradle.properties")
              ),
              //language=groovy
              buildGradle(
                """
                  subprojects {
                    apply plugin: 'groovy'

                    repositories {
                      mavenCentral()
                    }

                    dependencies {
                      testImplementation "org.spockframework:spock-core:$spockVersion"
                      testImplementation "org.codehaus.groovy:groovy-all:$groovyVersion"
                    }
                  }
                  """
              ),
              mavenProject("app",
                //language=groovy
                buildGradle(
                  """
                    dependencies {
                      testImplementation "org.spockframework:spock-spring:$spockVersion"
                    }
                    """
                )
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

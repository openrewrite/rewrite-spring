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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class SpringDataStaxCassandraTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .expectedCyclesThatMakeChanges(1)
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.spring")
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3")
          );
    }

    @Test
    void groupIdChangeCassandraBom() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>fooservice</artifactId>
                    <version>1.0-SNAPSHOT</version>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.datastax.oss</groupId>
                                <artifactId>java-driver-bom</artifactId>
                                <version>4.17.0</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """,
              spec -> spec.after(actual -> {
                  assertThat(actual).containsPattern("<version>4.18.\\d+</version>");
                  assertThat(actual).containsPattern("<groupId>org.apache.cassandra</groupId>");
                  return actual;
              })
            )
          )
        );
    }

    @Test
    void groupIdChangeCassandraLibraryJar() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>fooservice</artifactId>
                    <version>1.0-SNAPSHOT</version>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.datastax.oss</groupId>
                                <artifactId>java-driver-core</artifactId>
                                <version>4.17.0</version>
                                <type>jar</type>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """,
              spec -> spec.after(actual -> {
                  assertThat(actual).containsPattern("<version>4.18.\\d+</version>");
                  assertThat(actual).containsPattern("<groupId>org.apache.cassandra</groupId>");
                  return actual;
              })
            )
          )
        );
    }

    @Test
    void noGroupIdChangeForDatastax() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>fooservice</artifactId>
                    <version>1.0-SNAPSHOT</version>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.datastax.oss</groupId>
                                <artifactId>native-protocol</artifactId>
                                <version>1.5.1</version>
                                <type>jar</type>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """,
              spec -> spec.after(actual -> {
                  assertThat(actual).containsPattern("<version>1.5.1</version>");
                  assertThat(actual).containsPattern("<groupId>com.datastax.oss</groupId>");
                  return actual;
              })
            )
          )
        );
    }
}

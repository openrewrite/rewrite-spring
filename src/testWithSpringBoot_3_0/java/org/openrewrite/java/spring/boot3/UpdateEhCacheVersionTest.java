/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.gradle.Assertions;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

@Issue("https://github.com/openrewrite/rewrite-spring/issues/376")
class UpdateEhCacheVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.ehcache")
            .build()
            .activateRecipes("org.openrewrite.java.ehcache.MigrateEhcache"));
    }

    @Nested
    class Maven {
        @DocumentExample
        @Test
        void addVersionNumber() {
            rewriteRun(
              pomXml(
                //language=xml
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.ehcache</groupId>
                        <artifactId>ehcache</artifactId>
                      </dependency>
                    </dependencies>
                  </project>
                  """,
                spec -> spec.after(pom -> {
                    Matcher version = Pattern.compile("3.\\d+.\\d+").matcher(pom);
                    assertThat(version.find()).describedAs("Expected 3.x.x in %s", pom).isTrue();
                    //language=xml
                    return String.format(
                      """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>0.0.1-SNAPSHOT</version>
                          <dependencies>
                            <dependency>
                              <groupId>org.ehcache</groupId>
                              <artifactId>ehcache</artifactId>
                              <version>%s</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """, version.group(0));
                })
              )
            );
        }
    }

    @Nested
    class Gradle {

        @DocumentExample
        @Test
        void addVersionNumber() {
            rewriteRun(spec -> spec.beforeRecipe(withToolingApi()),
              Assertions.buildGradle(
                //language=groovy
                """
                  plugins {
                    id 'java'
                    id 'org.springframework.boot' version '2.7.13'
                    id 'io.spring.dependency-management' version '1.0.15.RELEASE'
                  }
                                       
                  repositories {
                     mavenCentral()
                  }
                                       
                  dependencies {
                      implementation 'org.ehcache:ehcache'
                  }
                  """,
                spec -> spec.after(gradle -> {
                    Matcher version = Pattern.compile("3.\\d+.\\d+").matcher(gradle);
                    assertThat(version.find()).describedAs("Expected 3.x.x in %s", gradle).isTrue();
                    //language=groovy
                    return String.format(
                      """
                        plugins {
                          id 'java'
                          id 'org.springframework.boot' version '2.7.13'
                          id 'io.spring.dependency-management' version '1.0.15.RELEASE'
                        }
                                             
                        repositories {
                           mavenCentral()
                        }
                                             
                        dependencies {
                            implementation 'org.ehcache:ehcache:%s'
                        }
                        """, version.group(0));
                })
              )
            );
        }
    }
}

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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.withToolingApi;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

@Issue("https://github.com/openrewrite/rewrite-spring/issues/274")
class UpdateMysqlDriverArtifactIdTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.java.spring")
          .build()
          .activateRecipes("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7"));
    }

    @Nested
    class Maven {
        @DocumentExample
        @Test
        void switchArtifactIdAndUpdateVersionNumber() {
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
                        <groupId>mysql</groupId>
                        <artifactId>mysql-connector-java</artifactId>
                        <version>8.0.30</version>
                        <scope>runtime</scope>
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
                        <groupId>com.mysql</groupId>
                        <artifactId>mysql-connector-j</artifactId>
                        <version>8.0.33</version>
                        <scope>runtime</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """));
        }

        @Test
        void doNotPinWhenNotVersioned() {
            rewriteRun(
              pomXml(
                //language=xml
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>2.7.7</version>
                      <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <dependencies>
                      <dependency>
                        <groupId>mysql</groupId>
                        <artifactId>mysql-connector-java</artifactId>
                        <scope>runtime</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """,
                spec -> spec.after(pom -> {
                    Matcher version = Pattern.compile("2.7.\\d+").matcher(pom);
                    assertThat(version.find()).describedAs("Expected 2.7.x in %s", pom).isTrue();
                    //language=xml
                    return String.format(
                      """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>demo</artifactId>
                          <version>0.0.1-SNAPSHOT</version>
                          <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>%s</version>
                            <relativePath/> <!-- lookup parent from repository -->
                          </parent>
                          <dependencies>
                            <dependency>
                              <groupId>com.mysql</groupId>
                              <artifactId>mysql-connector-j</artifactId>
                              <scope>runtime</scope>
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
    @Issue("https://github.com/openrewrite/rewrite-spring/issues/375")
    class Gradle {

        @DocumentExample
        @Test
        void switchArtifactIdAndUpdateVersionNumber() {
            rewriteRun(spec -> spec.beforeRecipe(withToolingApi()),
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
                      runtimeOnly 'mysql:mysql-connector-java:8.0.30'
                  }
                  """,
                """
                  plugins {
                    id 'java'
                  }
                                       
                  repositories {
                     mavenCentral()
                  }
                                       
                  dependencies {
                      runtimeOnly 'com.mysql:mysql-connector-j:8.0.33'
                  }
                  """)
            );
        }

        @Test
        void doNotPinWhenNotVersioned() {
            rewriteRun(spec -> spec.beforeRecipe(withToolingApi()),
              buildGradle(
                //language=gradle
                """
                  plugins {
                    id 'java'
                    id 'org.springframework.boot' version '2.6.1'
                    id 'io.spring.dependency-management' version '1.0.11.RELEASE'
                  }
                  
                  repositories {
                     mavenCentral()
                  }
                  
                  dependencies {
                      runtimeOnly 'mysql:mysql-connector-java'
                  }
                  """, spec -> spec.after(gradle -> {
                    Matcher version = Pattern.compile("2\\.7\\.\\d+").matcher(gradle);
                    assertThat(version.find()).describedAs("Expected 2.7.x in %s", gradle).isTrue();
                    //language=gradle
                    return """
                  plugins {
                    id 'java'
                    id 'org.springframework.boot' version '%s'
                    id 'io.spring.dependency-management' version '1.0.15.RELEASE'
                  }
                  
                  repositories {
                     mavenCentral()
                  }
                  
                  dependencies {
                      runtimeOnly 'com.mysql:mysql-connector-j'
                  }
                  """.formatted(version.group());
                  })
              )
            );
        }
    }
}

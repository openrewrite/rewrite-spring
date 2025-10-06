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
package org.openrewrite.gradle.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.FileAttributes;
import org.openrewrite.Tree;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.other;
import static org.openrewrite.test.SourceSpecs.text;

class UpdateGradleTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7")
          .beforeRecipe(withToolingApi())
          .allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Gradle, "6.7")));
    }

    @DocumentExample
    @Test
    void upgradeGradleWrapperAndPlugins() {
        rewriteRun(
          buildGradle(
            //language=gradle
            """
              plugins {
                  id "java"
                  id "org.springframework.boot" version "2.6.15"
                  id "io.spring.dependency-management" version "1.0.11.RELEASE"
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation "org.springframework.boot:spring-boot-starter-web"
              }
              """,
                spec -> spec.after(gradle -> {
                    Matcher version = Pattern.compile("2\\.7\\.\\d+").matcher(gradle);
                    assertThat(version.find()).describedAs("Expected 2.7.x in %s", gradle).isTrue();
                    //language=gradle
                    return """
              plugins {
                  id "java"
                  id "org.springframework.boot" version "%s"
                  id "io.spring.dependency-management" version "1.0.15.RELEASE"
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation "org.springframework.boot:spring-boot-starter-web"
              }
              tasks.withType(Test).configureEach {
                  useJUnitPlatform()
              }
              """.formatted(version.group());
                })
            //language=gradle
          ),
          properties(
            //language=properties
            """
              distributionBase=GRADLE_USER_HOME
              distributionPath=wrapper/dists
              distributionUrl=https\\://services.gradle.org/distributions/gradle-6.7-bin.zip
              zipStoreBase=GRADLE_USER_HOME
              zipStorePath=wrapper/dists
              """,
            //language=properties
            """
              distributionBase=GRADLE_USER_HOME
              distributionPath=wrapper/dists
              distributionUrl=https\\://services.gradle.org/distributions/gradle-6.9.4-bin.zip
              zipStoreBase=GRADLE_USER_HOME
              zipStorePath=wrapper/dists
              distributionSha256Sum=3e240228538de9f18772a574e99a0ba959e83d6ef351014381acd9631781389a
              """,
            spec -> spec.path("gradle/wrapper/gradle-wrapper.properties")
          ),
          text(
            "",
            spec -> spec.path("gradlew")
              .after(after -> {
                  assertThat(after).isNotBlank();
                  return after + "\n";
              }).afterRecipe(gradlew -> {
                  assertThat(gradlew.getFileAttributes())
                    .matches(FileAttributes::isReadable);
                  assertThat(gradlew.getFileAttributes().isExecutable()).isTrue();
              })
          ),
          text(
            "",
            spec -> spec.path("gradlew.bat")
              .after(after -> {
                  assertThat(after).isNotBlank();
                  return after + "\n";
              })
          ),
          other(
            "",
            spec -> spec.path("gradle/wrapper/gradle-wrapper.jar")
          )
        );
    }

    @Test
    void dontAddSpringDependencyManagementWhenUsingGradlePlatform() {
        rewriteRun(
          buildGradle(
            //language=gradle
            """
              plugins {
                  id "java"
                  id "org.springframework.boot" version "2.6.15"
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation platform("org.springframework.boot:spring-boot-dependencies:2.6.15")
                  implementation "org.springframework.boot:spring-boot-starter-web"
              }
              """,
                spec -> spec.after(gradle -> {
                    Matcher version = Pattern.compile("2\\.7\\.\\d+").matcher(gradle);
                    assertThat(version.find()).describedAs("Expected 2.7.x in %s", gradle).isTrue();
                    //language=gradle
                    return """
              plugins {
                  id "java"
                  id "org.springframework.boot" version "%s"
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation platform("org.springframework.boot:spring-boot-dependencies:%s")
                  implementation "org.springframework.boot:spring-boot-starter-web"
              }
              tasks.withType(Test).configureEach {
                  useJUnitPlatform()
              }
              """.formatted(version.group(), version.group());
                })
          ),
          properties(
            //language=properties
            """
              distributionBase=GRADLE_USER_HOME
              distributionPath=wrapper/dists
              distributionUrl=https\\://services.gradle.org/distributions/gradle-6.7-bin.zip
              zipStoreBase=GRADLE_USER_HOME
              zipStorePath=wrapper/dists
              """,
            //language=properties
            """
              distributionBase=GRADLE_USER_HOME
              distributionPath=wrapper/dists
              distributionUrl=https\\://services.gradle.org/distributions/gradle-6.9.4-bin.zip
              zipStoreBase=GRADLE_USER_HOME
              zipStorePath=wrapper/dists
              distributionSha256Sum=3e240228538de9f18772a574e99a0ba959e83d6ef351014381acd9631781389a
              """,
            spec -> spec.path("gradle/wrapper/gradle-wrapper.properties")
          ),
          text(
            "",
            spec -> spec.path("gradlew")
              .after(after -> {
                  assertThat(after).isNotBlank();
                  return after + "\n";
              }).afterRecipe(gradlew -> {
                  assertThat(gradlew.getFileAttributes())
                      .matches(FileAttributes::isReadable);
                  assertThat(gradlew.getFileAttributes().isExecutable()).isTrue();
              })
          ),
          text(
            "",
            spec -> spec.path("gradlew.bat")
              .after(after -> {
                  assertThat(after).isNotBlank();
                  return after + "\n";
              })
          ),
          other(
            "",
            spec -> spec.path("gradle/wrapper/gradle-wrapper.jar")
          )
        );
    }
}

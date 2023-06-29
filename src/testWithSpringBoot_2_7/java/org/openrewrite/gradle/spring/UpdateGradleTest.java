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
package org.openrewrite.gradle.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.Tree;
import org.openrewrite.config.Environment;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.withToolingApi;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.other;
import static org.openrewrite.test.SourceSpecs.text;

class UpdateGradleTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.spring.boot2")
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7"))
          .beforeRecipe(withToolingApi())
          .allSources(source -> source.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Gradle, "6.7")));
    }

    @Test
    void upgradeGradleWrapperAndPlugins() {
        rewriteRun(
          buildGradle(
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
            """
              plugins {
                  id "java"
                  id "org.springframework.boot" version "2.7.13"
                  id "io.spring.dependency-management" version "1.0.15.RELEASE"
              }
              
              repositories {
                  mavenCentral()
              }
              
              dependencies {
                  implementation "org.springframework.boot:spring-boot-starter-web"
              }
              """
          ),
          properties(
            """
              distributionBase=GRADLE_USER_HOME
              distributionPath=wrapper/dists
              distributionUrl=https\\://services.gradle.org/distributions/gradle-6.7-bin.zip
              zipStoreBase=GRADLE_USER_HOME
              zipStorePath=wrapper/dists
              """,
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
                  assertThat(gradlew.getFileAttributes().isReadable()).isTrue();
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
            """
              plugins {
                  id "java"
                  id "org.springframework.boot" version "2.7.13"
              }
              
              repositories {
                  mavenCentral()
              }
              
              dependencies {
                  implementation platform("org.springframework.boot:spring-boot-dependencies:2.7.13")
                  implementation "org.springframework.boot:spring-boot-starter-web"
              }
              """
          ),
          properties(
            """
              distributionBase=GRADLE_USER_HOME
              distributionPath=wrapper/dists
              distributionUrl=https\\://services.gradle.org/distributions/gradle-6.7-bin.zip
              zipStoreBase=GRADLE_USER_HOME
              zipStorePath=wrapper/dists
              """,
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
                  assertThat(gradlew.getFileAttributes().isReadable()).isTrue();
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

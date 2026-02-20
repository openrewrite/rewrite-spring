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
package org.openrewrite.gradle.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;

class UpgradeSpringBoot40GradleBuildscriptTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0")
          .beforeRecipe(withToolingApi())
          .expectedCyclesThatMakeChanges(2)
          .cycles(2);
    }

    @Test
    void upgradesLegacyBuildscriptApplyPluginPattern() {
        rewriteRun(
          buildGradle(
            //language=gradle
            """
              buildscript {
                  ext {
                      springBootVersion = '3.5.5'
                  }
                  repositories {
                      mavenCentral()
                  }
                  dependencies {
                      classpath group: 'org.springframework.boot', name: 'spring-boot-gradle-plugin', version: "${springBootVersion}"
                  }
              }

              apply plugin: 'java'
              apply plugin: 'org.springframework.boot'
              apply plugin: 'io.spring.dependency-management'

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation(group: 'org.springframework.boot', name: 'spring-boot-starter-web')
                  implementation(group: 'org.springframework.boot', name: 'spring-boot-starter-actuator')
                  testImplementation(group: 'org.springframework.boot', name: 'spring-boot-starter-test')
              }
              """,
            spec -> spec.after(gradle -> {
                assertThat(gradle).as("Spring Boot version should be upgraded to 4.0.x")
                  .containsPattern("springBootVersion = '4\\.0\\.\\d+'");
                assertThat(gradle).as("spring-boot-starter-web should be renamed to spring-boot-starter-webmvc")
                  .contains("spring-boot-starter-webmvc");
                assertThat(gradle).as("spring-boot-starter-web should no longer be present")
                  .doesNotContain("spring-boot-starter-web'");
                return gradle;
            })
          )
        );
    }

    @Test
    void upgradesLegacyBuildscriptWithGroupAndAdditionalDeps() {
        rewriteRun(
          buildGradle(
            //language=gradle
            """
              group 'com.example'
              buildscript {
                  ext {
                      springBootVersion = '3.5.5'
                  }
                  repositories {
                      mavenCentral()
                  }
                  dependencies {
                      classpath group: 'org.springframework.boot', name: 'spring-boot-gradle-plugin', version: "${springBootVersion}"
                  }
              }

              apply plugin: 'java'
              apply plugin: 'org.springframework.boot'
              apply plugin: 'io.spring.dependency-management'

              sourceCompatibility = 17

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation(group: 'org.springframework.boot', name: 'spring-boot-starter-web')
                  implementation(group: 'org.springframework.boot', name: 'spring-boot-starter-actuator')
                  implementation(group: 'org.springframework.boot', name: 'spring-boot-starter-data-jpa')
                  implementation(group: 'org.springframework.boot', name: 'spring-boot-configuration-processor')
                  testImplementation(group: 'org.springframework.boot', name: 'spring-boot-starter-test')
                  implementation(group: 'org.projectlombok', name: 'lombok', version: '1.18.30')
                  annotationProcessor(group: 'org.projectlombok', name: 'lombok', version: '1.18.30')
              }
              """,
            spec -> spec.after(gradle -> {
                assertThat(gradle).as("Spring Boot version should be upgraded to 4.0.x")
                  .containsPattern("springBootVersion = '4\\.0\\.\\d+'");
                assertThat(gradle).as("spring-boot-starter-web should be renamed to spring-boot-starter-webmvc")
                  .contains("spring-boot-starter-webmvc");
                return gradle;
            })
          )
        );
    }
}

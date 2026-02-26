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
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.properties.Assertions.properties;

@Issue("https://github.com/moderneinc/customer-requests/issues/1920")
class UpgradeSpringBoot_4_0GradleTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.java.spring")
          .build()
          .activateRecipes("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0"));
    }

    @Test
    void doNotPinBomManagedStarters() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'org.springframework.boot' version '3.4.1'
                  id 'io.spring.dependency-management' version '1.1.7'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation 'org.springframework.boot:spring-boot-starter-webflux'
                  testImplementation 'org.springframework.boot:spring-boot-starter-test'
              }
              """,
            spec -> spec.after(gradle -> {
                assertThat(gradle)
                  .as("BOM-managed starters should NOT get explicit versions pinned")
                  .doesNotContainPattern("spring-boot-starter-webflux:\\d")
                  .doesNotContainPattern("spring-boot-starter-test:\\d");
                assertThat(gradle)
                  .as("Spring Boot plugin should be upgraded to 4.0.x")
                  .containsPattern("'org.springframework.boot' version '4\\.0\\.\\d+'");
                return gradle;
            })
          )
        );
    }

    @Test
    void doNotPinRenamedStarters() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'org.springframework.boot' version '3.4.1'
                  id 'io.spring.dependency-management' version '1.1.7'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation 'org.springframework.boot:spring-boot-starter-web'
                  implementation 'org.springframework.boot:spring-boot-starter-webflux'
                  testImplementation 'org.springframework.boot:spring-boot-starter-test'
              }
              """,
            spec -> spec.after(gradle -> {
                assertThat(gradle)
                  .as("Renamed starters should NOT get explicit versions pinned")
                  .doesNotContainPattern("spring-boot-starter-webmvc:\\d")
                  .doesNotContainPattern("spring-boot-starter-webflux:\\d")
                  .doesNotContainPattern("spring-boot-starter-test:\\d");
                assertThat(gradle)
                  .as("spring-boot-starter-web should be renamed to spring-boot-starter-webmvc")
                  .contains("spring-boot-starter-webmvc");
                return gradle;
            })
          )
        );
    }

    @Test
    void updateSpringBootVersionProperty() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          properties(
            """
              springBootVersion=3.4.1
              """,
            spec -> spec.path("gradle.properties")
              .after(props -> {
                  assertThat(props)
                    .as("springBootVersion property should be updated to 4.0.x")
                    .containsPattern("springBootVersion=4\\.0\\.\\d+");
                  return props;
              })
          ),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'org.springframework.boot' version "${springBootVersion}"
                  id 'io.spring.dependency-management' version '1.1.7'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation 'org.springframework.boot:spring-boot-starter-webflux'
                  testImplementation 'org.springframework.boot:spring-boot-starter-test'
              }
              """,
            spec -> spec.after(gradle -> {
                assertThat(gradle)
                  .as("BOM-managed starters should NOT get explicit versions pinned")
                  .doesNotContainPattern("spring-boot-starter-webflux:\\d")
                  .doesNotContainPattern("spring-boot-starter-test:\\d");
                return gradle;
            })
          )
        );
    }

    @Test
    void doNotPinStartersWithGStringVersion() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          properties(
            """
              springBootVersion=3.4.1
              """,
            spec -> spec.path("gradle.properties")
              .after(props -> {
                  assertThat(props)
                    .as("springBootVersion property should be updated to 4.0.x")
                    .containsPattern("springBootVersion=4\\.0\\.\\d+");
                  return props;
              })
          ),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'org.springframework.boot' version "${springBootVersion}"
                  id 'io.spring.dependency-management' version '1.1.7'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation "org.springframework.boot:spring-boot-starter-web:${springBootVersion}"
                  implementation 'org.springframework.boot:spring-boot-starter-webflux'
                  testImplementation 'org.springframework.boot:spring-boot-starter-test'
              }
              """,
            spec -> spec.after(gradle -> {
                assertThat(gradle)
                  .as("BOM-managed starters should NOT get explicit versions pinned")
                  .doesNotContainPattern("spring-boot-starter-webflux:\\d")
                  .doesNotContainPattern("spring-boot-starter-test:\\d");
                assertThat(gradle)
                  .as("Renamed starter with GString version should not get hardcoded version")
                  .doesNotContain("spring-boot-starter-webmvc:4.0");
                return gradle;
            })
          )
        );
    }
}

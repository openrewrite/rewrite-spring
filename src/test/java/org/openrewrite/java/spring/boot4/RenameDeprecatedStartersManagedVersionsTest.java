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
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;

class RenameDeprecatedStartersManagedVersionsTest implements RewriteTest {

    @Test
    void renameStarterWithoutVersionWhenDepMgmtPluginPresent() {
        rewriteRun(
          spec -> spec
            .recipeFromResource(
              "/META-INF/rewrite/spring-boot-40.yml",
              "org.openrewrite.java.spring.boot4.RenameDeprecatedStartersManagedVersions")
            .beforeRecipe(withToolingApi()),
          mavenProject("sample",
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
                }
                """,
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
                    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
                }
                """
            )
          )
        );
    }

    @Test
    void renameStarterWithVersionWhenDepMgmtPluginAbsent() {
        rewriteRun(
          spec -> spec
            .recipe(new org.openrewrite.gradle.ChangeDependency(
              "org.springframework.boot", "spring-boot-starter-web",
              null, "spring-boot-starter-webmvc", "4.0.x",
              null, null, null))
            .beforeRecipe(withToolingApi()),
          mavenProject("sample",
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
                    implementation 'org.springframework.boot:spring-boot-starter-web:3.4.1'
                }
                """,
              spec -> spec.after(gradle -> {
                  assertThat(gradle)
                    .contains("spring-boot-starter-webmvc")
                    .containsPattern("spring-boot-starter-webmvc:4\\.0\\.\\d+");
                  return gradle;
              })
            )
          )
        );
    }
}

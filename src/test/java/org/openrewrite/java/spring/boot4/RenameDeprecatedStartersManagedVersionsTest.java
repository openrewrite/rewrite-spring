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
            .recipeFromResources("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0")
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
              spec -> spec.after(gradle ->
                assertThat(gradle)
                  .contains("spring-boot-starter-webmvc")
                  .doesNotContain("spring-boot-starter-webmvc:")
                  .doesNotContain("spring-boot-starter-web'")
                  .doesNotContain("spring-boot-starter-web:")
                  .actual())
            )
          )
        );
    }

    @Test
    void renameStarterWithVersionWhenDepMgmtPluginAbsent() {
        rewriteRun(
          spec -> spec
            .recipeFromResources("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0")
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
              spec -> spec.after(gradle ->
                assertThat(gradle)
                  .contains("spring-boot-starter-webmvc")
                  .containsPattern("spring-boot-starter-webmvc:4\\.0\\.\\d+")
                  .doesNotContain("spring-boot-starter-web'")
                  .doesNotContain("spring-boot-starter-web:")
                  .actual())
            )
          )
        );
    }
}

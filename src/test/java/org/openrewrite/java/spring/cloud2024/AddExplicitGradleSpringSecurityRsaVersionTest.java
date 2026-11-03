/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.cloud2024;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;

class AddExplicitGradleSpringSecurityRsaVersionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddExplicitGradleSpringSecurityRsaVersion()).beforeRecipe(withToolingApi());
    }

    @Test
    void kotlinPlatform() {
        rewriteRun(
          buildGradleKts(
            """
              plugins { java }
              repositories { mavenCentral() }
              dependencies {
                  implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2023.0.2"))
                  implementation("org.springframework.security:spring-security-rsa")
              }
              """,
            """
              plugins { java }
              repositories { mavenCentral() }
              dependencies {
                  implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2023.0.2"))
                  implementation("org.springframework.security:spring-security-rsa:1.1.3")
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void explicitVersionsAndUnrelatedDependency() {
            rewriteRun(
              buildGradle(
                """
                  plugins { id 'java' }
                  repositories { mavenCentral() }
                  def rsaVersion = '1.1.2'
                  dependencies {
                      implementation platform('org.springframework.cloud:spring-cloud-dependencies:2023.0.2')
                      implementation "org.springframework.security:spring-security-rsa:$rsaVersion"
                      implementation 'org.springframework.cloud:spring-cloud-context'
                  }
                  """
              ),
              buildGradleKts(
                """
                  plugins { java }
                  repositories { mavenCentral() }
                  dependencies {
                      implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2023.0.2"))
                      implementation("org.springframework.security:spring-security-rsa:1.1.2")
                  }
                  """
              )
            );
        }
    }
}

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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringFramework_6_0Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0")
          .beforeRecipe(withToolingApi());
    }

    @Test
    void upgradesSpringKafka() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.kafka</groupId>
                          <artifactId>spring-kafka</artifactId>
                          <version>2.9.11</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(actual ->
              assertThat(actual).containsPattern("<version>3\\.0\\.\\d+</version>").actual())
          )
        );
    }

    @Test
    void upgradesSpringIntegration() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.integration</groupId>
                          <artifactId>spring-integration-core</artifactId>
                          <version>5.5.20</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(actual ->
              assertThat(actual).containsPattern("<version>6\\.0\\.\\d+</version>").actual())
          )
        );
    }

    @Test
    void upgradesDependencyManagementPluginInGradle() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'io.spring.dependency-management' version '1.0.6.RELEASE'
              }
              """,
            spec -> spec.after(actual ->
              assertThat(actual).containsPattern("'1\\.1\\.\\d+'").actual())
          )
        );
    }
}

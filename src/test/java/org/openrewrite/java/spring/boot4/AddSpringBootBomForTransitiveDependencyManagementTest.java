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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class AddSpringBootBomForTransitiveDependencyManagementTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddSpringBootBomForTransitiveDependencyManagement("4.0.0"));
    }

    @Test
    void rejectSelectorsOutsideBoot4() {
        assertThat(new AddSpringBootBomForTransitiveDependencyManagement("3.0.0").validate().isValid()).isFalse();
        assertThat(new AddSpringBootBomForTransitiveDependencyManagement("5.0.x").validate().isValid()).isFalse();
        assertThat(new AddSpringBootBomForTransitiveDependencyManagement("latest.release").validate().isValid()).isFalse();
        assertThat(new AddSpringBootBomForTransitiveDependencyManagement("4.0.x").validate().isValid()).isTrue();
    }

    @Test
    void overrideTransitiveBootManagementBeforeVendorBom() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0</version>
                  <dependencyManagement>
                      <!-- Preserve management comments too. -->
                      <dependencies>
                          <!-- Keep the vendor BOM and its comment together. -->
                          <dependency>
                              <groupId>io.github.jhipster</groupId>
                              <artifactId>jhipster-dependencies</artifactId>
                              <version>3.7.1</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0</version>
                  <dependencyManagement>
                      <!-- Preserve management comments too. -->
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.boot</groupId>
                              <artifactId>spring-boot-dependencies</artifactId>
                              <version>4.0.0</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                          <!-- Keep the vendor BOM and its comment together. -->
                          <dependency>
                              <groupId>io.github.jhipster</groupId>
                              <artifactId>jhipster-dependencies</artifactId>
                              <version>3.7.1</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.afterRecipe(pom -> assertThat(pom.getMarkers().findFirst(MavenResolutionResult.class).orElseThrow()
              .getPom().getManagedVersion("org.springframework.boot", "spring-boot", null, null)).isEqualTo("4.0.0"))
          )
        );
    }

    @Test
    void leaveVendorBomWithoutBootUsageAlone() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>io.github.jhipster</groupId>
                              <artifactId>jhipster-dependencies</artifactId>
                              <version>3.7.1</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """
          )
        );
    }

    @Test
    void preserveNewerBootParent() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>4.0.1</version>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void leaveDirectBootBomForTheVersionUpgradeRecipe() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.boot</groupId>
                              <artifactId>spring-boot-dependencies</artifactId>
                              <version>3.5.0</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }
}

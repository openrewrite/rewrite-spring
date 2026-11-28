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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeMyBatisToSpringBoot_3_0Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot3.UpgradeMyBatisToSpringBoot_3_0");
    }

    @DocumentExample
    @Test
    void upgradePageHelperStarterInProperty() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                  <pagehelper.spring.boot.starter.version>1.2.5</pagehelper.spring.boot.starter.version>
                </properties>
                <dependencies>
                  <dependency>
                    <groupId>com.github.pagehelper</groupId>
                    <artifactId>pagehelper-spring-boot-starter</artifactId>
                    <version>${pagehelper.spring.boot.starter.version}</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .containsPattern("<pagehelper.spring.boot.starter.version>1\\.4\\.\\d+</pagehelper.spring.boot.starter.version>")
              .actual())
          )
        );
    }

    @Test
    void upgradePageHelperAutoconfigure() {
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
                    <groupId>com.github.pagehelper</groupId>
                    <artifactId>pagehelper-spring-boot-autoconfigure</artifactId>
                    <version>1.3.0</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .containsPattern("<version>1\\.4\\.\\d+</version>")
              .actual())
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void pageHelperStarterAlreadyOnNewerVersion() {
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
                        <groupId>com.github.pagehelper</groupId>
                        <artifactId>pagehelper-spring-boot-starter</artifactId>
                        <version>2.1.0</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }
    }
}

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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.other;
import static org.openrewrite.yaml.Assertions.yaml;

class MergeBootstrapYamlWithApplicationYamlTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MergeBootstrapYamlWithApplicationYaml());
    }

    @DocumentExample
    @Test
    void mergeBootstrapYAML() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                name: test
                """,
              spec -> spec.path("application.yaml")
            ),
            //language=yaml
            yaml(
              """
                name: test
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yaml")
            )
          )
        );
    }

    @Test
    void mergeBootstrapYML() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                name: test
                """,
              spec -> spec.path("application.yml")
            ),
            //language=yaml
            yaml(
              """
                name: test
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yml")
            )
          )
        );
    }

    @Test
    void mergeMultipleBootstrapDocuments() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                name: test
                other.document: true
                """,
              spec -> spec.path("application.yaml")
            ),
            //language=yaml
            yaml(
              """
                name: test
                ---
                other:
                  document: true
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yaml")
            )
          )
        );
    }

    @Test
    void renameToApplicationYaml() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application:
                  name: main
                name: test
                """,
              spec -> spec.path("bootstrap.yaml")
                .afterRecipe(doc -> assertThat(doc.getSourcePath()).isEqualTo(Paths.get("src/main/resources/application.yaml")))
            )
          )
        );
    }

    @Test
    void doNotMergeExistingKeys() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                """,
              spec -> spec.path("application.yaml")
            ),
            //language=yaml
            yaml(
              """
                spring.application:
                  name: override
                ---
                spring.application.name: override
                ---
                spring:
                  application:
                    name: override
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yaml")
            )
          )
        );
    }

    @Test
    void keepProfileSpecificDocumentsSeparate() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring:
                  application.name: main
                """,
              """
                spring.application.name: main
                name: test
                ---
                spring.config.activate.on-profile: test
                name: profile-test
                other.document: false
                """,
              spec -> spec.path("application.yaml")
            ),
            //language=yaml
            yaml(
              """
                name: test
                ---
                spring.config.activate.on-profile: test
                name: profile-test
                other:
                  document: false
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yaml")
            )
          )
        );
    }

    @Test
    void doNotMergeWhenNotValidApplicationYaml() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            other("""
                spring:
                  application.name: main
                """,
              spec -> spec.path("application.yaml")),
            //language=yaml
            yaml(
              """
                name: test
                """,
              spec -> spec.path("bootstrap.yaml")
            )
          )
        );
    }

    @Test
    void doNotMergeWhenNotValidBootstrapYaml() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml("""
                spring:
                  application.name: main
                """,
              spec -> spec.path("application.yaml")),
            //language=yaml
            other(
              """
                name: test
                """,
              spec -> spec.path("bootstrap.yaml")
            )
          )
        );
    }

    @Test
    void mergeProfileSpecificBootstrapYaml() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                name: integ-test
                """,
              spec -> spec.path("application-integTest.yml")
            ),
            //language=yaml
            yaml(
              """
                name: integ-test
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap-integTest.yml")
            )
          )
        );
    }

    @Test
    void renameToProfileSpecificApplicationYaml() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          srcMainResources(
            //language=yaml
            yaml(
              """
                name: integ-test
                """,
              spec -> spec.path("bootstrap-integTest.yml")
                .afterRecipe(doc -> assertThat(doc.getSourcePath()).isEqualTo(Paths.get("src/main/resources/application-integTest.yml")))
            )
          )
        );
    }

    @Test
    void mergeBaseAndProfileTogether() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                name: base
                """,
              spec -> spec.path("application.yml")
            ),
            //language=yaml
            yaml(
              """
                name: base
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yml")
            ),
            //language=yaml
            yaml(
              """
                extra: baseline
                """,
              """
                extra: baseline
                name: profile
                """,
              spec -> spec.path("application-integTest.yml")
            ),
            //language=yaml
            yaml(
              """
                name: profile
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap-integTest.yml")
            )
          )
        );
    }

    @Test
    void doNotPairBootstrapProfileWithBaseApplication() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: main
                """,
              spec -> spec.path("application.yml")
            ),
            //language=yaml
            yaml(
              """
                name: integ-test
                """,
              spec -> spec.path("bootstrap-integTest.yml")
                .afterRecipe(doc -> assertThat(doc.getSourcePath()).isEqualTo(Paths.get("src/main/resources/application-integTest.yml")))
            )
          )
        );
    }

    @Test
    void keepLegacyProfileSpecificDocumentsSeparate() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                name: test
                ---
                spring.profiles: dev
                other: dev-only
                """,
              spec -> spec.path("application.yml")
            ),
            //language=yaml
            yaml(
              """
                name: test
                ---
                spring.profiles: dev
                other: dev-only
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yml")
            )
          )
        );
    }

    @Test
    void keepLeadingProfileSpecificDocumentSeparate() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                ---
                spring.config.activate.on-profile: dev
                name: dev
                ---
                spring.config.activate.on-profile: prod
                name: prod
                """,
              spec -> spec.path("application.yml")
            ),
            //language=yaml
            yaml(
              """
                spring.config.activate.on-profile: dev
                name: dev
                ---
                spring.config.activate.on-profile: prod
                name: prod
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yml")
            )
          )
        );
    }

    @Test
    void keepBootstrapWhenApplicationHasOnlyProfileSpecificDocuments() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.config.activate.on-profile: dev
                other: dev-only
                """,
              """
                name: test
                ---
                spring.config.activate.on-profile: dev
                other: dev-only
                """,
              spec -> spec.path("application.yml")
            ),
            //language=yaml
            yaml(
              """
                name: test
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yml")
            )
          )
        );
    }

    @Test
    void mergeIntoFirstNonProfileSpecificDocument() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              """
                spring.config.activate.on-profile: dev
                other: dev-only
                ---
                spring.application.name: main
                """,
              """
                spring.config.activate.on-profile: dev
                other: dev-only
                ---
                spring.application.name: main
                name: test
                """,
              spec -> spec.path("application.yml")
            ),
            //language=yaml
            yaml(
              """
                name: test
                """,
              doesNotExist(),
              spec -> spec.path("bootstrap.yml")
            )
          )
        );
    }

    @Test
    void mergePerModule() {
        rewriteRun(
          mavenProject("a",
            srcMainResources(
              //language=yaml
              yaml(
                """
                  spring.application.name: a
                  """,
                """
                  spring.application.name: a
                  name: a
                  """,
                spec -> spec.path("application.yml")
              ),
              //language=yaml
              yaml(
                """
                  name: a
                  """,
                doesNotExist(),
                spec -> spec.path("bootstrap.yml")
              )
            )
          ),
          mavenProject("b",
            srcMainResources(
              //language=yaml
              yaml(
                """
                  name: b
                  """,
                spec -> spec.path("bootstrap.yml")
                  .afterRecipe(doc -> assertThat(doc.getSourcePath()).isEqualTo(Paths.get("b/src/main/resources/application.yml")))
              )
            )
          )
        );
    }

    @Test
    void doNotMergeWhenSpringCloudStarterBootstrapPresentGradle() {
        rewriteRun(spec -> spec.beforeRecipe(withToolingApi()),
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
                  implementation 'org.springframework.cloud:spring-cloud-starter-bootstrap:3.1.0'
              }
              """
          ),
          //language=yaml
          yaml(
            """
              spring.application.name: main
              """,
            spec -> spec.path("src/main/resources/application.yaml")
          ),
          //language=yaml
          yaml(
            """
              name: test
              """,
            spec -> spec.path("src/main/resources/bootstrap.yaml")
          )
        );
    }

    @Test
    void doNotMergeWhenSpringCloudStarterBootstrapPresent() {
        rewriteRun(
          mavenProject("project",
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
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-starter-bootstrap</artifactId>
                            <version>3.1.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainResources(
              //language=yaml
              yaml(
                """
                  spring.application.name: main
                  """,
                spec -> spec.path("application.yaml")
              ),
              //language=yaml
              yaml(
                """
                  name: test
                  """,
                spec -> spec.path("bootstrap.yaml")
              )
            )
          )
        );
    }
}

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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Tree;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.java.Assertions.srcTestResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class ConvertPropertiesToYamlTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertPropertiesToYaml(null));
    }

    private static String skipMessage(String conflictingPath) {
        return "~~(Skipped: a corresponding YAML file already exists at '" + conflictingPath + "'. " +
          "Merge these properties into it manually; when both files exist the `.properties` values " +
          "take precedence, so converting automatically could change the effective configuration.)~~>";
    }

    @DocumentExample
    @Test
    void singlePropertyIsConvertedToNestedYaml() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "server.port=8080",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    port: 8080
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void emptyPropertiesFileIsDeletedWithoutCreatingYaml() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "",
                null,
                spec -> spec.path("application.properties")
              )
            )
          )
        );
    }

    @Test
    void ymlExtensionOptionGeneratesYmlFile() {
        rewriteRun(
          spec -> spec.recipe(new ConvertPropertiesToYaml("yml")),
          mavenProject("project",
            srcMainResources(
              properties(
                "server.port=8080",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    port: 8080
                  """,
                spec -> spec.path("application.yml")
              )
            )
          )
        );
    }

    @Test
    void profileSpecificPropertiesFileIsConverted() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "spring.datasource.url=jdbc:h2:mem:devdb",
                null,
                spec -> spec.path("application-dev.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  spring:
                    datasource:
                      url: "jdbc:h2:mem:devdb"
                  """,
                spec -> spec.path("application-dev.yaml")
              )
            )
          )
        );
    }

    @Test
    void multipleProfileFilesAreConvertedIndependently() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "server.port=8081",
                null,
                spec -> spec.path("application-dev.properties")
              ),
              properties(
                "server.port=8082",
                null,
                spec -> spec.path("application-prod.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    port: 8081
                  """,
                spec -> spec.path("application-dev.yaml")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    port: 8082
                  """,
                spec -> spec.path("application-prod.yaml")
              )
            )
          )
        );
    }

    @Test
    void testResourcesAreAlsoConverted() {
        rewriteRun(
          mavenProject("project",
            srcTestResources(
              properties(
                "server.port=9090",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    port: 9090
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void mainAndTestResourcesAreBothConverted() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "server.port=8080",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    port: 8080
                  """,
                spec -> spec.path("application.yaml")
              )
            ),
            srcTestResources(
              properties(
                "server.port=9090",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    port: 9090
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void skipWithMessageWhenYmlAlreadyExists() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "server.port=8080",
                skipMessage("project/src/main/resources/application.yml") + "server.port=8080",
                spec -> spec.path("application.properties")
              ),
              yaml(
                //language=yaml
                "server:\n  port: 9090",
                spec -> spec.path("application.yml")
              )
            )
          )
        );
    }

    @Test
    void skipWithMessageWhenYamlAlreadyExists() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "server.port=8080",
                skipMessage("project/src/main/resources/application.yaml") + "server.port=8080",
                spec -> spec.path("application.properties")
              ),
              yaml(
                //language=yaml
                "server:\n  port: 9090",
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void skipWithMessageForProfileFileWhenYamlAlreadyExists() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "server.port=8080",
                skipMessage("project/src/main/resources/application-dev.yaml") + "server.port=8080",
                spec -> spec.path("application-dev.properties")
              ),
              yaml(
                //language=yaml
                "server:\n  port: 9090",
                spec -> spec.path("application-dev.yaml")
              )
            )
          )
        );
    }

    @Test
    void skipWhenExistingYamlWasNotParsedAsYaml() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "server.port=8080",
                skipMessage("project/src/main/resources/application.yml") + "server.port=8080",
                spec -> spec.path("application.properties")
              ),
              text(
                "server:\n  port: 9090",
                spec -> spec.path("application.yml")
              )
            )
          )
        );
    }

    @Test
    void skipWithMessageWhenReferencedFromJavaSources() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=java
              java(
                """
                  class Config {
                      String location = "classpath:application.properties";
                  }
                  """
              )
            ),
            srcMainResources(
              properties(
                "server.port=8080",
                "~~(Skipped: this file is referenced from Java sources (e.g. `@PropertySource`), " +
                  "which cannot load YAML files. Update those references before converting.)~~>server.port=8080",
                spec -> spec.path("application.properties")
              )
            )
          )
        );
    }

    @Test
    void javaReferenceToOtherProfileDoesNotBlockConversion() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=java
              java(
                """
                  class Config {
                      String location = "classpath:application-dev.properties";
                  }
                  """
              )
            ),
            srcMainResources(
              properties(
                "server.port=8080",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    port: 8080
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void nonApplicationPropertiesFilesAreUntouched() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "greeting=hello",
                spec -> spec.path("messages.properties")
              ),
              properties(
                "bean.name=foo",
                spec -> spec.path("applicationContext.properties")
              ),
              properties(
                "greeting=hallo",
                spec -> spec.path("application_de.properties")
              )
            )
          )
        );
    }

    @Test
    void propertiesFileOutsideAnySourceSetIsUntouched() {
        rewriteRun(
          properties(
            "server.port=8080",
            spec -> spec.path("application.properties")
          )
        );
    }

    @Test
    void propertiesFileMarkedAsSpringConfigIsConverted() {
        rewriteRun(
          properties(
            "server.port=8080",
            null,
            spec -> spec.path("svc/config/application.properties")
              .markers(new SpringConfigFile(Tree.randomId()))
          ),
          yaml(
            doesNotExist(),
            //language=yaml
            """
              server:
                port: 8080
              """,
            spec -> spec.path("svc/config/application.yaml")
          )
        );
    }

    @Test
    void multiModuleProjectConvertsEachModuleIndependently() {
        rewriteRun(
          mavenProject("parent",
            mavenProject("service",
              srcMainResources(
                properties(
                  "server.port=8081",
                  null,
                  spec -> spec.path("application.properties")
                ),
                yaml(
                  doesNotExist(),
                  //language=yaml
                  """
                    server:
                      port: 8081
                    """,
                  spec -> spec.path("application.yaml")
                )
              )
            ),
            mavenProject("client",
              srcMainResources(
                properties(
                  "server.port=8082",
                  null,
                  spec -> spec.path("application.properties")
                ),
                yaml(
                  doesNotExist(),
                  //language=yaml
                  """
                    server:
                      port: 8082
                    """,
                  spec -> spec.path("application.yaml")
                )
              )
            )
          )
        );
    }
}

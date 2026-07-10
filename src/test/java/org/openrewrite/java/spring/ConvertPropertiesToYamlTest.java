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
    void multiplePropertiesAreConvertedToNestedYaml() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  server.port=8080
                  spring.application.name=myapp
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    port: 8080
                  spring:
                    application:
                      name: myapp
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
    void valueContainingColonIsQuoted() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "spring.datasource.url=jdbc:h2:mem:db",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  spring:
                    datasource:
                      url: "jdbc:h2:mem:db"
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void valueContainingHashIsQuoted() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "app.message=hello # world",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  app:
                    message: "hello # world"
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void emptyValueMapsToEmptyString() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "server.context-path=",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    context-path: ""
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void yamlBooleanLikeValuesAreQuotedToPreserveStringSemantics() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  app.a=yes
                  app.b=off
                  app.c=on
                  app.d=true
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  app:
                    a: "yes"
                    b: "off"
                    c: "on"
                    d: true
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void yamlNullLikeValuesAreQuoted() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  app.a=null
                  app.b=~
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  app:
                    a: "null"
                    b: "~"
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void valuesReTypedByYamlAreQuotedOthersStayPlain() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  app.a=0x1A
                  app.b=1_000
                  app.c=+1
                  app.d=2001-12-14
                  app.e=1.50
                  app.f=8080
                  app.g=1.5
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  app:
                    a: "0x1A"
                    b: "1_000"
                    c: "+1"
                    d: "2001-12-14"
                    e: "1.50"
                    f: 8080
                    g: 1.5
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void octalLikeValueIsQuoted() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "app.file-mask=0755",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  app:
                    file-mask: "0755"
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void escapedNewlineAndTabAreTranslated() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "app.message=line1\\nline2\\tend",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                """
                  app:
                    message: "line1\\nline2\\tend"
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void escapedBackslashAndUnicodeAreTranslated() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                "app.path=C:\\\\data\\u0021",
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                """
                  app:
                    path: "C:\\\\data!"
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void escapedKeysAreUnescaped() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                """
                  my\\ key=1
                  a\\:b=2
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                """
                  my key: 1
                  a:b: 2
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void keyThatIsAlsoAPrefixOfOtherKeysStaysLiteral() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  a=1
                  a.b=2
                  b.c.d=3
                  b.c=4
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  a: 1
                  a.b: 2
                  b:
                    c.d: 3
                    c: 4
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void commentLinesInPropertiesArePreserved() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  # header comment
                  server.port=8080
                  ! also a comment
                  spring.application.name=myapp
                  # trailing comment
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  server:
                    # header comment
                    port: 8080
                  spring:
                    application:
                      # also a comment
                      name: myapp
                  # trailing comment
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void indexedKeysAreConvertedToYamlSequence() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  my.list[0]=a
                  other.key=x
                  my.list[1]=b
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  my:
                    list:
                      - a
                      - b
                  other:
                    key: x
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void outOfOrderIndexedKeysAreSortedIntoSequence() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  my.list[1]=b
                  my.list[0]=a
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  my:
                    list:
                      - a
                      - b
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void sequenceValuesAreQuotedWhenNeeded() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  app.urls[0]=jdbc:h2:mem:db
                  app.urls[1]=plain
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  app:
                    urls:
                      - "jdbc:h2:mem:db"
                      - plain
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void nonContiguousIndexedKeysRemainFlat() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  my.list[0]=a
                  my.list[2]=c
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  my:
                    list[0]: a
                    list[2]: c
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void objectListKeysAreConvertedToYamlSequence() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  my.servers[0].host=alpha
                  my.servers[0].port=8080
                  my.servers[1].host=beta
                  my.servers[1].port=9090
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  my:
                    servers:
                      - host: alpha
                        port: 8080
                      - host: beta
                        port: 9090
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void nestedListInsideObjectListIsConverted() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  my.servers[0].host=alpha
                  my.servers[0].tags[0]=x
                  my.servers[0].tags[1]=y
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  my:
                    servers:
                      - host: alpha
                        tags:
                          - x
                          - y
                  """,
                spec -> spec.path("application.yaml")
              )
            )
          )
        );
    }

    @Test
    void nonContiguousObjectListKeysRemainFlat() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                //language=properties
                """
                  my.servers[0].host=alpha
                  my.servers[2].host=gamma
                  """,
                null,
                spec -> spec.path("application.properties")
              ),
              yaml(
                doesNotExist(),
                //language=yaml
                """
                  my:
                    servers[0]:
                      host: alpha
                    servers[2]:
                      host: gamma
                  """,
                spec -> spec.path("application.yaml")
              )
            )
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

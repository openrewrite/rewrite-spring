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
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class MarkAdditionalSpringConfigFilesTest implements RewriteTest {

    @Test
    void marksMatchingPropertiesFileOutsideSourceSet() {
        //language=properties
        String content = """
          server.servlet-path=/tmp/my-server-path
          """;
        rewriteRun(
          spec -> spec.recipe(new MarkAdditionalSpringConfigFiles(List.of("**/properties/*.properties"))),
          //language=properties
          properties(
            content,
            content,
            spec -> spec.path("svc/properties/base-prod.properties")
              .afterRecipe(file ->
                assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isPresent())
          )
        );
    }

    @Test
    void marksMatchingYamlFileOutsideSourceSet() {
        //language=yaml
        String content = """
          server:
            port: 8080
          """;
        rewriteRun(
          spec -> spec.recipe(new MarkAdditionalSpringConfigFiles(List.of("**/config/*.yml"))),
          //language=yaml
          yaml(
            content,
            content,
            spec -> spec.path("svc/config/base-prod.yml")
              .afterRecipe(file ->
                assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isPresent())
          )
        );
    }

    @Test
    void doesNotMarkFilesAlreadyInSourceSet() {
        rewriteRun(
          spec -> spec.recipe(new MarkAdditionalSpringConfigFiles(List.of("**/*.properties"))),
          mavenProject("project",
            srcMainResources(
              //language=properties
              properties(
                """
                  server.servlet-path=/tmp/my-server-path
                  """,
                spec -> spec.path("application.properties")
                  .afterRecipe(file ->
                    assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isNotPresent())
              )
            )
          )
        );
    }

    @Test
    void doesNotMarkNonMatchingFiles() {
        rewriteRun(
          spec -> spec.recipe(new MarkAdditionalSpringConfigFiles(List.of("**/properties/*.properties"))),
          //language=properties
          properties(
            """
              foo=bar
              """,
            spec -> spec.path("somewhere/else/base-prod.properties")
              .afterRecipe(file ->
                assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isNotPresent())
          )
        );
    }

    @Test
    void onlyMarksMatchingFilesAmongMultiple() {
        //language=yaml
        String matchingYaml = """
          server:
            port: 8080
          """;
        rewriteRun(
          spec -> spec.recipe(new MarkAdditionalSpringConfigFiles(List.of("**/config/*.yml"))),
          //language=yaml
          yaml(
            matchingYaml,
            matchingYaml,
            spec -> spec.path("svc/config/base-prod.yml")
              .afterRecipe(file ->
                assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isPresent())
          ),
          //language=yaml
          yaml(
            """
              name: CI
              on: push
              """,
            spec -> spec.path(".github/workflows/ci.yml")
              .afterRecipe(file ->
                assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isNotPresent())
          )
        );
    }

    @Test
    void idempotentOnRepeatedRuns() {
        //language=properties
        String content = """
          server.servlet-path=/tmp/my-server-path
          """;
        rewriteRun(
          spec -> spec.recipes(
            new MarkAdditionalSpringConfigFiles(List.of("**/properties/*.properties")),
            new MarkAdditionalSpringConfigFiles(List.of("**/properties/*.properties"))
          ),
          //language=properties
          properties(
            content,
            content,
            spec -> spec.path("svc/properties/base-prod.properties")
              .afterRecipe(file ->
                assertThat(file.getMarkers().getMarkers().stream()
                  .filter(m -> m instanceof SpringConfigFile)
                  .count()).isEqualTo(1))
          )
        );
    }

    @Test
    void enablesChangeSpringPropertyKeyOnFileOutsideSourceSet() {
        rewriteRun(
          spec -> spec.recipes(
            new MarkAdditionalSpringConfigFiles(List.of("**/properties/*.properties")),
            new ChangeSpringPropertyKey("server.servlet-path", "server.servlet.path", null)
          ),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            """
              server.servlet.path=/tmp/my-server-path
              """,
            spec -> spec.path("svc/properties/base-prod.properties")
              .afterRecipe(file ->
                assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isPresent())
          )
        );
    }

    @Test
    void changeSpringPropertyKeyAloneSkipsFileOutsideSourceSet() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey(
            "server.servlet-path", "server.servlet.path", null)),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            spec -> spec.path("svc/properties/base-prod.properties")
              .afterRecipe(file ->
                assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isNotPresent())
          )
        );
    }
}

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
        rewriteRun(
          spec -> spec.recipe(new MarkAdditionalSpringConfigFiles(List.of("**/properties/*.properties")))
            .expectedCyclesThatMakeChanges(1),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            spec -> spec.path("svc/properties/base-prod.properties")
              .afterRecipe(file ->
                assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isPresent())
          )
        );
    }

    @Test
    void marksMatchingYamlFileOutsideSourceSet() {
        rewriteRun(
          spec -> spec.recipe(new MarkAdditionalSpringConfigFiles(List.of("**/config/*.yml")))
            .expectedCyclesThatMakeChanges(1),
          //language=yaml
          yaml(
            """
              server:
                port: 8080
              """,
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
        rewriteRun(
          spec -> spec.recipe(new MarkAdditionalSpringConfigFiles(List.of("**/config/*.yml")))
            .expectedCyclesThatMakeChanges(1),
          //language=yaml
          yaml(
            """
              server:
                port: 8080
              """,
            spec -> spec.path("svc/config/base-prod.yml")
              .afterRecipe(file ->
                assertThat(file.getMarkers().findFirst(SpringConfigFile.class)).isPresent())
          )
        );
    }

    @Test
    void idempotentOnRepeatedRuns() {
        rewriteRun(
          spec -> spec.recipes(
            new MarkAdditionalSpringConfigFiles(List.of("**/properties/*.properties")),
            new MarkAdditionalSpringConfigFiles(List.of("**/properties/*.properties"))
          ).expectedCyclesThatMakeChanges(1),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
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
            new org.openrewrite.java.spring.ChangeSpringPropertyKey(
              "server.servlet-path", "server.servlet.path", null)
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
          )
        );
    }

    @Test
    void changeSpringPropertyKeyAloneSkipsFileOutsideSourceSet() {
        rewriteRun(
          spec -> spec.recipe(new org.openrewrite.java.spring.ChangeSpringPropertyKey(
            "server.servlet-path", "server.servlet.path", null)),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            spec -> spec.path("svc/properties/base-prod.properties")
          )
        );
    }
}

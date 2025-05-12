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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.test.SourceSpecs.other;
import static org.openrewrite.yaml.Assertions.yaml;

class MergeBootstrapYamlWithApplicationYamlTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MergeBootstrapYamlWithApplicationYaml());
    }

    @Test
    void mergeBootstrap() {
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
              null,
              spec -> spec.path("bootstrap.yaml")
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
              null,
              spec -> spec.path("bootstrap.yaml")
            )
          )
        );
    }

    @Test
    void createsApplicationYaml() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml(
              null,
              """
                spring.application.name: main
                name: test
                """,
              spec -> spec.path("application.yaml")
            ),
            //language=yaml
            yaml(
              """
                spring.application:
                  name: main
                name: test
                """,
              null,
              spec -> spec.path("bootstrap.yaml")
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
              null,
              spec -> spec.path("bootstrap.yaml")
            )
          )
        );
    }

    @Test
    void doNotMergeProfileSpecificDocuments() {
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
              null,
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
}

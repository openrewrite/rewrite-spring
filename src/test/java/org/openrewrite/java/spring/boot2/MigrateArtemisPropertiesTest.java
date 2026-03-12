/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.Tree;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.marker.Markers;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class MigrateArtemisPropertiesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateArtemisProperties())
          .allSources(sourceSpec -> sourceSpec.markers(new JavaSourceSet(Tree.randomId(), "main", emptyList(), emptyMap())));
    }

    @DocumentExample
    @Test
    void migratesHostAndPortToBrokerUrl() {
        rewriteRun(
          //language=properties
          properties(
            """
              spring.artemis.host=myhost
              spring.artemis.port=1234
              """,
            """
              spring.artemis.broker-url=tcp://myhost:1234
              """
          )
        );
    }

    @Test
    void migratesHostOnlyWithDefaultPort() {
        rewriteRun(
          //language=properties
          properties(
            """
              spring.artemis.host=myhost
              """,
            """
              spring.artemis.broker-url=tcp://myhost:61616
              """
          )
        );
    }

    @Test
    void migratesPortOnlyWithDefaultHost() {
        rewriteRun(
          //language=properties
          properties(
            """
              spring.artemis.port=1234
              """,
            """
              spring.artemis.broker-url=tcp://localhost:1234
              """
          )
        );
    }

    @Test
    void migratesHostAndPortInYaml() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              spring:
                artemis:
                  host: myhost
                  port: 1234
              """,
            """
              spring:
                artemis:
                  broker-url: tcp://myhost:1234
              """
          )
        );
    }

    @Test
    void doesNotModifyUnrelatedProperties() {
        rewriteRun(
          //language=properties
          properties(
            """
              spring.artemis.mode=native
              """
          )
        );
    }

    @Test
    void doesNotModifyUnrelatedFiles() {
        rewriteRun(
          //language=properties
          properties(
            """
              spring.artemis.host=myhost
              spring.artemis.port=1234
              """,
            spec -> spec.mapBeforeRecipe(file -> file.withMarkers(Markers.EMPTY))
          )
        );
    }
}

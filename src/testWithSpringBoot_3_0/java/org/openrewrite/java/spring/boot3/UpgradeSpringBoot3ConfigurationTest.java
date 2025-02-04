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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class UpgradeSpringBoot3ConfigurationTest implements RewriteTest {

    @DocumentExample
    @Test
    void moveMaxHttpHeaderSize() {
        rewriteRun(
          spec -> spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.spring")
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot3.MigrateMaxHttpHeaderSize")
          ),
          mavenProject("test",
            srcMainResources(
              properties(
                //language=properties
                """
                  # application.properties
                  server.max-http-header-size=10KB
                  """,
                //language=properties
                """
                  # application.properties
                  server.max-http-request-header-size=10KB
                  """,
                s -> s.path("src/main/resources/application.properties")
              ),
              yaml(
                //language=properties
                """
                      server:
                        max-http-header-size: 10KB
                  """,
                //language=properties
                """
                      server:
                        max-http-request-header-size: 10KB
                  """,
                s -> s.path("src/main/resources/application.yml")
              )
            )
          )
        );
    }
}

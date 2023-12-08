/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.javaVersion;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class EnableVirtualThreadsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot3.EnableVirtualThreads")
          .allSources(source -> source.markers(javaVersion(21)));
    }

    @Test
    @DocumentExample
    void enableVirtualThreadsProperties() {
        rewriteRun(
          //language=properties
          properties(
            "",
            """
              spring.threads.virtual.enabled=true
              """,
            s -> s.path("src/main/resources/application.properties")
          )
        );
    }

    @Test
    @DocumentExample
    void enableVirtualThreadsYaml() {
        rewriteRun(
          //language=yaml
          yaml(
            "",
            """
              spring:
                threads:
                  virtual:
                    enabled: true
              """,
            s -> s.path("src/main/resources/application.yml")
          )
        );
    }

    @Test
    void dontEnableVirtualThreadsIfDisabled() {
        rewriteRun(
          //language=properties
          properties(
            """
              spring.threads.virtual.enabled=false
              """,
            s -> s.path("src/main/resources/application.properties")
          ),
          //language=yaml
          yaml(
            """
              spring:
                threads:
                  virtual:
                    enabled: true
              """,
            s -> s.path("src/main/resources/application.yml")
          )
        );
    }
}

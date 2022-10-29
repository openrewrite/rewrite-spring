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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.yaml.Assertions.yaml;

@Disabled
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
                spring:
                  application.name: main
                ---
                spring:
                  config:
                    activate:
                      on-profile: test
                name: test
                """,
              """
                spring.application.name: main
                ---
                spring.config.activate.on-profile: test
                name: test
                """,
              spec -> spec.path("application.yaml")
            ),
            //language=yaml
            yaml(
              """
                spring.application:
                  name: not the name
                ---
                spring:
                    config:
                        activate:
                            on-profile: test
                name: test
                """,
              null,
              spec -> spec.path("bootstrap.yaml")
            )
          )
        );
    }
}

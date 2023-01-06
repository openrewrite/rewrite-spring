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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class PropertiesToKebabCaseTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PropertiesToKebabCase());
    }

    @Test
    void singleFlatProperty() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              "spring.main.showBanner: true",
              "spring.main.show-banner: true",
              spec -> spec.path("application.yaml")
            ),
            properties(
              "spring.main.showBanner=true",
              "spring.main.show-banner=true",
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void singleNestedProperty() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              """
                  spring:
                    main:
                      showBanner: true
                """,
              """
                  spring:
                    main:
                      show-banner: true
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void multipleFlatProperties() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              """
                  spring.main.showBanner: true
                  myCustom.propertyValue.GOES_HERE: example
                """,
              """
                  spring.main.show-banner: true
                  my-custom.property-value.goes-here: example
                """,
              spec -> spec.path("application.yaml")
            ),
            properties(
              """
                spring.main.showBanner=true
                myCustom.propertyValue.GOES_HERE=EXAMPLE
                """,
              """
                spring.main.show-banner=true
                my-custom.property-value.goes-here=EXAMPLE
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void multipleNestedProperties() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              """
                  SOME.EXAMPLE_CUSTOM:
                    firstNested:
                      firstValue: first-example
                      secondValue: second-example
                    second_nested: second_nested_example
                  another:
                    exampleGoes:
                      HERE: example
                """,
              """
                  some.example-custom:
                    first-nested:
                      first-value: first-example
                      second-value: second-example
                    second-nested: second_nested_example
                  another:
                    example-goes:
                      here: example
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void doNotChange() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              """
                  some.example-custom:
                    first-nested:
                      first-value: first-example
                      second-value: second-example
                    second-nested: second_nested_example
                  another:
                    example-goes:
                      here: example
                """,
              spec -> spec.path("application.yaml")
            ),
            properties(
              """
                some.example-custom.first-nested.first-value=first-example
                some.example-custom.first-nested.second-value=second-example
                some.example-custom.second-nested=second_nested_example
                another.example-goes.here=example
                """
            )
          )
        );
    }
}

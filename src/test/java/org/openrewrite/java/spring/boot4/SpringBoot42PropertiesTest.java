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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;

class SpringBoot42PropertiesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot4.SpringBootProperties_4_2");
    }

    @DocumentExample
    @Test
    void migrateTomcatServletProperty() {
        rewriteRun(
          srcMainResources(
            properties(
              "server.tomcat.redirect-context-root=false",
              "server.tomcat.servlet.redirect-context-root=false"
            )
          )
        );
    }

    @Test
    void doNotTurnAnIgnoredMeterListIntoABoolean() {
        rewriteRun(
          srcMainResources(
            properties("management.metrics.observations.ignored-meters=http.server.requests")
          )
        );
    }
}

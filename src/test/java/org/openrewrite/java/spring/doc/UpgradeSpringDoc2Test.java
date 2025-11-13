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
package org.openrewrite.java.spring.doc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringDoc2Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.springdoc.UpgradeSpringDoc_2");
    }

    @DocumentExample
    @Test
    void upgradeMaven() {
        rewriteRun(
          pomXml(
            // language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springdoc</groupId>
                          <artifactId>springdoc-openapi</artifactId>
                          <version>1.5.13</version>
                      </dependency>
                      <dependency>
                          <groupId>org.springdoc</groupId>
                          <artifactId>springdoc-openapi-common</artifactId>
                          <version>1.5.13</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            after -> after.after(actual -> actual).afterRecipe(doc -> {
                MavenResolutionResult maven = doc.getMarkers().findFirst(MavenResolutionResult.class).orElseThrow();
                assertThat(maven.getPom().getRequestedDependencies())
                  .allSatisfy(d1 -> assertThat(d1.getVersion()).startsWith("2."));
            })
          )
        );
    }
}

/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.springdoc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.openrewrite.maven.Assertions.pomXml;

class SwaggerToSpringDocTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.springdoc.SwaggerToSpringDoc");
    }

    @Test
    @DocumentExample
    void upgradeMaven() {
        rewriteRun(
          // language=xml
          pomXml(
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
                  </dependencies>
              </project>
              """,
            after -> after.after(actual -> {
                String version = Pattern.compile("<version>(2\\.3\\..*)</version>")
                  .matcher(actual)
                  .results()
                  .map(m -> m.group(1))
                  .findFirst()
                  .orElseThrow();
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.springdoc</groupId>
                              <artifactId>springdoc-openapi</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(version);
            })
          )
        );
    }
}

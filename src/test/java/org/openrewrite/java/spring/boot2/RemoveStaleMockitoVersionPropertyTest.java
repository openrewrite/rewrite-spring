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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class RemoveStaleMockitoVersionPropertyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot2.RemoveStaleMockitoVersionProperty");
    }

    @DocumentExample
    @Test
    void removeStaleMockitoVersion() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>fooservice</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <mockito.version>2.18.3</mockito.version>
                  </properties>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>fooservice</artifactId>
                  <version>1.0-SNAPSHOT</version>
              </project>
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.18.3", "3.12.4", "4.0.0", "4.2.0"})
    void removeVersionsPredatingMockitoBom(String version) {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>fooservice</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <mockito.version>%s</mockito.version>
                  </properties>
              </project>
              """.formatted(version),
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>fooservice</artifactId>
                  <version>1.0-SNAPSHOT</version>
              </project>
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.3.0", "4.11.0", "5.2.0"})
    void retainVersionsManagedByMockitoBom(String version) {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>fooservice</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <mockito.version>%s</mockito.version>
                  </properties>
              </project>
              """.formatted(version)
          )
        );
    }
}

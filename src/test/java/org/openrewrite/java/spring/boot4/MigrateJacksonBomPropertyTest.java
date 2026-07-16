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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class MigrateJacksonBomPropertyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJacksonBomProperty());
    }

    @Test
    void renameJackson2Override() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>jackson-app</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <jackson-bom.version>2.21.1</jackson-bom.version>
                  </properties>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>jackson-app</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <jackson-2-bom.version>2.21.1</jackson-2-bom.version>
                  </properties>
              </project>
              """
          )
        );
    }

    @Test
    void leaveJackson3OverrideUntouched() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>jackson-app</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <jackson-bom.version>3.1.0</jackson-bom.version>
                  </properties>
              </project>
              """
          )
        );
    }

    @Test
    void leaveExistingJackson2BomOverrideUntouched() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>jackson-app</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <jackson-2-bom.version>2.21.1</jackson-2-bom.version>
                  </properties>
              </project>
              """
          )
        );
    }
}

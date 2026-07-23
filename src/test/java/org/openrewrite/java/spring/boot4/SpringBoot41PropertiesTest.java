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

import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringBoot41PropertiesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot4.SpringBootProperties_4_1");
    }

    @Test
    void migrateProperties() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                logging.file.clean-history-on-start=true
                logging.file.max-history=7
                logging.file.max-size=10MB
                logging.file.total-size-cap=1GB
                logging.pattern.rolling-file-name=app-%d.log
                """,
              """
                logging.logback.rollingpolicy.clean-history-on-start=true
                logging.logback.rollingpolicy.max-history=7
                logging.logback.rollingpolicy.max-file-size=10MB
                logging.logback.rollingpolicy.total-size-cap=1GB
                logging.logback.rollingpolicy.file-name-pattern=app-%d.log
                """
            )
          )
        );
    }

    @Test
    void migrateYaml() {
        rewriteRun(
          srcMainResources(
            yaml(
              """
                logging:
                  file:
                    clean-history-on-start: true
                    max-history: 7
                    max-size: 10MB
                    total-size-cap: 1GB
                  pattern:
                    rolling-file-name: app-%d.log
                """,
              """
                logging:
                  logback.rollingpolicy.clean-history-on-start: true
                  logback.rollingpolicy.max-history: 7
                  logback.rollingpolicy.max-file-size: 10MB
                  logback.rollingpolicy.total-size-cap: 1GB
                  logback.rollingpolicy.file-name-pattern: app-%d.log
                """
            )
          )
        );
    }
}

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

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class MigrateDatabaseCredentialsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDatabaseCredentials());
    }

    @Test
    void yamlMigration() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              spring:
                liquibase:
                  url: host
              """,
            """
              spring.liquibase:
                url: host
                username: ${spring.datasource.username}
                password: ${spring.datasource.password}
              """
          )
        );
    }

    @Test
    void propertiesMigration() {
        rewriteRun(
          //language=properties
          properties(
            """
              spring.liquibase.url=host
              """,
            """
              spring.liquibase.url=host
              spring.liquibase.username=${spring.datasource.username}
              spring.liquibase.password=${spring.datasource.password}
              """
          )
        );
    }
}

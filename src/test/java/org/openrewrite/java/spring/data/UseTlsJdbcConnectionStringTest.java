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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class UseTlsJdbcConnectionStringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseTlsJdbcConnectionString(null, 5021, 15021, "sslConnection=true;"));
    }

    @Test
    void useTls() {
        rewriteRun(
          //language=yaml
          yaml(
            """
                spring:
                    datasource:
                      url: 'jdbc:db2://10.2.1.101:5021/DB2INST1:currentSchema=DEV;commandTimeout=30;'
              """,
            """
                spring:
                    datasource:
                      url: 'jdbc:db2://10.2.1.101:15021/DB2INST1:currentSchema=DEV;commandTimeout=30;sslConnection=true;'
              """
            ),
          //language=properties
          properties(
            """
              spring.datasource.url=jdbc:db2://10.2.1.101:5021/DB2INST1:currentSchema=DEV;commandTimeout=30;
              """,
            """
              spring.datasource.url=jdbc:db2://10.2.1.101:15021/DB2INST1:currentSchema=DEV;commandTimeout=30;sslConnection=true;
              """
            )
        );
    }

    @Test
    void oldPortDoesNotMatch() {
        rewriteRun(
          //language=yaml
          yaml(
            """
                spring:
                    datasource:
                      url: 'jdbc:db2://10.2.1.101:5000/DB2INST1:currentSchema=DEV;commandTimeout=30;'
              """
          )
        );
    }

    @Test
    void allowCustomPropertyKey() {
        rewriteRun(
          spec -> spec.recipe(new UseTlsJdbcConnectionString("my.custom.url", 5021, 15021, "sslConnection=true;")),
          yaml(
            """
              my:
                custom:
                  url: 'jdbc:db2://10.2.1.101:5021/DB2INST1:currentSchema=DEV;commandTimeout=30;'
              """,
            """
              my:
                custom:
                  url: 'jdbc:db2://10.2.1.101:15021/DB2INST1:currentSchema=DEV;commandTimeout=30;sslConnection=true;'
              """
          )
        );
    }
}

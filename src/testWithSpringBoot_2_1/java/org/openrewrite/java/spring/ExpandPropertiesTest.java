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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class ExpandPropertiesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExpandProperties(null));
    }

    @DocumentExample
    @Test
    void expandProperties() {
        rewriteRun(
          yaml(
            //language=yml
            """
              management: test
              spring.application:
                name: main
                description: a description
              """,
            //language=yml
            """
              management: test
              spring:
                application:
                  name: main
                  description: a description
              """,
            spec -> spec.path("application.yml")
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/135")
    @Test
    void duplicatePropertiesShouldBeCoalesced() {
        rewriteRun(
          yaml(
            //language=yml
            """
              management: test
              spring:
                mail:
                  protocol: smtp
                  properties.mail.smtp.connection-timeout: 1000
                  properties.mail.smtp.timeout: 2000
                  properties.mail.smtp.write-timeout: 3000
              """,
            //language=yml
            """
              management: test
              spring:
                mail:
                  protocol: smtp
                  properties:
                    mail:
                      smtp:
                        connection-timeout: 1000
                        timeout: 2000
                        write-timeout: 3000
              """,
            spec -> spec.path("application.yml")
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/161")
    @Test
    void duplicatePropertyHasDotSyntax() {
        rewriteRun(
          yaml(
            //language=yml
            """
              server.context-path: /context
              server:
                port: 8888
              customize:
                group1: value1
              """,
            //language=yml
            """
              server:
                context-path: /context
                port: 8888
              customize:
                group1: value1
              """,
            spec -> spec.path("application.yml")
          )
        );
    }
}

/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.amqp;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class UseTlsAmqpConnectionStringTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseTlsAmqpConnectionString(null, 5672, 5671, null));
    }

    @Test
    void useTls() {
        rewriteRun(
          yaml(
            """
              spring:
                rabbitmq:
                  addresses: host1:5672
              """,
            """
              spring:
                rabbitmq:
                  addresses: host1:5671
                  ssl:
                    enabled: true
              """,
            spec -> spec.path("application.yml")
          ),
          properties(
            """
              spring.rabbitmq.addresses=host1:5672
              """,
            """
              spring.rabbitmq.addresses=host1:5671
              spring.rabbitmq.ssl.enabled=true
              """,
            spec -> spec.path("application.properties")
          )
        );
    }

    @Test
    void multipleAddresses() {
        rewriteRun(
          yaml(
            """
              spring:
                rabbitmq:
                  addresses: host1:5672,host2:5672
              """,
            """
              spring:
                rabbitmq:
                  addresses: host1:5671,host2:5671
                  ssl:
                    enabled: true
              """,
            spec -> spec.path("application.yml")
          ),
          properties(
            """
              spring.rabbitmq.addresses=host1:5672,host2:5672
              """,
            """
              spring.rabbitmq.addresses=host1:5671,host2:5671
              spring.rabbitmq.ssl.enabled=true
              """,
            spec -> spec.path("application.properties")
          )
        );
    }

    @Test
    void uri() {
        rewriteRun(
          yaml(
            """
              spring:
                rabbitmq:
                  addresses: amqp://host1:5672,amqp://host2:5672
              """,
            """
              spring:
                rabbitmq:
                  addresses: amqps://host1:5671,amqps://host2:5671
              """,
            spec -> spec.path("application.yml")
          ),
          properties(
            """
              spring.rabbitmq.addresses=amqp://host1:5672,amqp://host2:5672
              """,
            """
              spring.rabbitmq.addresses=amqps://host1:5671,amqps://host2:5671
              """,
            spec -> spec.path("application.properties")
          )
        );
    }
}

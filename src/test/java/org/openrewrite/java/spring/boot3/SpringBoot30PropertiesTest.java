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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringBoot30PropertiesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot3.SpringBootProperties_3_0");
    }

    @DocumentExample
    @Test
    void renameRedisPoolProperties() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                spring.redis.host=localhost
                spring.redis.lettuce.pool.max-active=8
                spring.redis.lettuce.pool.max-wait=-1ms
                spring.redis.jedis.pool.min-idle=0
                """,
              """
                spring.data.redis.host=localhost
                spring.data.redis.lettuce.pool.max-active=8
                spring.data.redis.lettuce.pool.max-wait=-1ms
                spring.data.redis.jedis.pool.min-idle=0
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void renameRedisPoolPropertiesInYaml() {
        rewriteRun(
          srcMainResources(
            yaml(
              """
                spring:
                  redis:
                    lettuce:
                      pool:
                        max-idle: 8
                        min-idle: 0
                """,
              """
                spring:
                  data:
                    redis:
                      lettuce:
                        pool:
                          max-idle: 8
                          min-idle: 0
                """,
              spec -> spec.path("application.yml")
            )
          )
        );
    }
}

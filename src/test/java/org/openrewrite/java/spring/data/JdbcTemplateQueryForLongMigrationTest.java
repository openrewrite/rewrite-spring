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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JdbcTemplateQueryForLongMigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JdbcTemplateQueryForLongMigration())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-jdbc-4", "spring-beans"));
    }

    @DocumentExample
    @Test
    void simpleTransformation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.jdbc.core.JdbcTemplate;

              class Test {
                  void test(JdbcTemplate jdbcTemplate) {
                      String query = "SELECT COUNT(*) FROM table";
                      Long count = jdbcTemplate.queryForLong(query);
                  }
              }
              """,
            """
              import org.springframework.jdbc.core.JdbcTemplate;

              class Test {
                  void test(JdbcTemplate jdbcTemplate) {
                      String query = "SELECT COUNT(*) FROM table";
                      Long count = jdbcTemplate.queryForObject(query, Long.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void transformationWithArgs() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.jdbc.core.JdbcTemplate;

              class Test {
                  void test(JdbcTemplate jdbcTemplate) {
                      String query = "SELECT COUNT(*) FROM table WHERE id = ?";
                      Long count = jdbcTemplate.queryForLong(query, 42);
                  }
              }
              """,
            """
              import org.springframework.jdbc.core.JdbcTemplate;

              class Test {
                  void test(JdbcTemplate jdbcTemplate) {
                      String query = "SELECT COUNT(*) FROM table WHERE id = ?";
                      Long count = jdbcTemplate.queryForObject(query, Long.class, 42);
                  }
              }
              """
          )
        );
    }
}

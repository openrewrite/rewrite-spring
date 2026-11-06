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

import org.junit.jupiter.api.Nested;
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
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-jdbc-4", "spring-tx-5", "spring-beans-5.+"));
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

              import java.util.Optional;

              class Test {
                  void test(JdbcTemplate jdbcTemplate) {
                      String query = "SELECT COUNT(*) FROM table";
                      Long count = Optional.ofNullable(jdbcTemplate.queryForObject(query, Long.class)).orElse(0L);
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
                      String query = "SELECT COUNT(*) FROM table WHERE id = ? AND name = ?";
                      Long count = jdbcTemplate.queryForLong(query, 42, "name");
                  }
              }
              """,
            """
              import org.springframework.jdbc.core.JdbcTemplate;

              import java.util.Optional;

              class Test {
                  void test(JdbcTemplate jdbcTemplate) {
                      String query = "SELECT COUNT(*) FROM table WHERE id = ? AND name = ?";
                      Long count = Optional.ofNullable(jdbcTemplate.queryForObject(query, Long.class, 42, "name")).orElse(0L);
                  }
              }
              """
          )
        );
    }

    @Test
    void argumentsWithArgumentTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.jdbc.core.JdbcTemplate;

              import java.sql.Types;

              class Test {
                  void test(JdbcTemplate jdbcTemplate) {
                      String query = "SELECT COUNT(*) FROM table WHERE id = ?";
                      Long count = jdbcTemplate.queryForLong(query, new Object[]{42}, new int[]{Types.INTEGER});
                  }
              }
              """,
            """
              import org.springframework.jdbc.core.JdbcTemplate;

              import java.sql.Types;
              import java.util.Optional;

              class Test {
                  void test(JdbcTemplate jdbcTemplate) {
                      String query = "SELECT COUNT(*) FROM table WHERE id = ?";
                      Long count = Optional.ofNullable(jdbcTemplate.queryForObject(query, new Object[]{42}, new int[]{Types.INTEGER}, Long.class)).orElse(0L);
                  }
              }
              """
          )
        );
    }

    @Test
    void sqlNullKeepsZeroForPrimitiveResult() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.jdbc.core.JdbcTemplate;

              class Test {
                  long test(JdbcTemplate jdbcTemplate) {
                      long maxId = jdbcTemplate.queryForLong("SELECT MAX(id) FROM table");
                      return maxId + 1;
                  }
              }
              """,
            """
              import org.springframework.jdbc.core.JdbcTemplate;

              import java.util.Optional;

              class Test {
                  long test(JdbcTemplate jdbcTemplate) {
                      long maxId = Optional.ofNullable(jdbcTemplate.queryForObject("SELECT MAX(id) FROM table", Long.class)).orElse(0L);
                      return maxId + 1;
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void alreadyMigrated() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.jdbc.core.JdbcTemplate;

                  class Test {
                      void test(JdbcTemplate jdbcTemplate) {
                          Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM table", Long.class);
                      }
                  }
                  """
              )
            );
        }
    }
}

/*
 * Copyright (c) The original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JdbcTemplateQueryForLongMigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JdbcTemplateQueryForLongMigration())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-jdbc", "spring-core", "spring-tx", "spring-beans", "slf4j-api"));
    }

    @Test
    void simpleTransformation() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          java(
            "import org.springframework.jdbc.core.JdbcTemplate;\n" +
            "\n" +
            "class Test {\n" +
            "    void test(JdbcTemplate jdbcTemplate) {\n" +
            "        String query = \"SELECT COUNT(*) FROM table\";\n" +
            "        Long count = jdbcTemplate.queryForLong(query);\n" +
            "    }\n" +
            "}",

            "import org.springframework.jdbc.core.JdbcTemplate;\n" +
            "\n" +
            "class Test {\n" +
            "    void test(JdbcTemplate jdbcTemplate) {\n" +
            "        String query = \"SELECT COUNT(*) FROM table\";\n" +
            "        Long count = jdbcTemplate.queryForObject(query, Long.class);\n" +
            "    }\n" +
            "}"
          )
        );
    }

    @Test
    void transformationWithArgs() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          java(
            "import org.springframework.jdbc.core.JdbcTemplate;\n" +
            "\n" +
            "class Test {\n" +
            "    void test(JdbcTemplate jdbcTemplate) {\n" +
            "        String query = \"SELECT COUNT(*) FROM table WHERE id = ?\";\n" +
            "        Long count = jdbcTemplate.queryForLong(query, 42);\n" +
            "    }\n" +
            "}",

            "import org.springframework.jdbc.core.JdbcTemplate;\n" +
            "\n" +
            "class Test {\n" +
            "    void test(JdbcTemplate jdbcTemplate) {\n" +
            "        String query = \"SELECT COUNT(*) FROM table WHERE id = ?\";\n" +
            "        Long count = jdbcTemplate.queryForObject(query, Long.class, 42);\n" +
            "    }\n" +
            "}"
          )
        );
    }
}

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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.java.Assertions.java;

class JdbcTemplateObjectArrayArgToVarArgsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JdbcTemplateObjectArrayArgToVarArgs())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-jdbc-4.1.+",
              "spring-tx-4.1.+",
              "spring-beans-5.+",
              "spring-core-5.+"
            )
          );
    }

    //language=java
    private final SourceSpecs user = java(
      """
        package abc;
        class User {
            private String name;
            private Integer age;

            Integer getAge() {
                return age;
            }
            void setAge(Integer age) {
                this.age = age;
            }
            String getName() {
                return name;
            }
            void setName(String name) {
                this.name = name;
            }
        }
        """
    );

    @DocumentExample
    @Test
    void reOrderQueryForObjectArgs() {
        //language=java
        rewriteRun(
          user,
          java(
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;
              import java.util.List;

              class MyDao {

                  JdbcTemplate jdbcTemplate;

                  User getUser(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", args, User.class);
                  }

                  User getUser2(String first, String last) {
                      Object[] args = new Object[]{first, last};
                       return jdbcTemplate.queryForObject("", args, (resultSet, i) -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      });
                  }
              }
              """,
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;
              import java.util.List;

              class MyDao {

                  JdbcTemplate jdbcTemplate;

                  User getUser(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", User.class, args);
                  }

                  User getUser2(String first, String last) {
                      Object[] args = new Object[]{first, last};
                       return jdbcTemplate.queryForObject("", (resultSet, i) -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      }, args);
                  }
              }
              """
          )
        );
    }

    @Test
    void reOrderQueryArgs() {
        //language=java
        rewriteRun(
          user,
          java(
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;
              import java.util.List;

              class MyDao {

                  JdbcTemplate jdbcTemplate;

                  List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? AND LAST = ?", args, (resultSet, i) -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      });
                  }

                  User getUserByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? && LAST = ?", args, resultSet -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      });
                  }

                  User getUserAge2(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      final User user = new User();
                      jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? && LAST = ?", args, rs -> {
                          user.setAge = rs.getInt("AGE");
                          user.setName = rs.getString("NAME");
                      });
                      return user;
                  }
              }
              """,
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;
              import java.util.List;

              class MyDao {

                  JdbcTemplate jdbcTemplate;

                  List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? AND LAST = ?", (resultSet, i) -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      }, args);
                  }

                  User getUserByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? && LAST = ?", resultSet -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      }, args);
                  }

                  User getUserAge2(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      final User user = new User();
                      jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? && LAST = ?", rs -> {
                          user.setAge = rs.getInt("AGE");
                          user.setName = rs.getString("NAME");
                      }, args);
                      return user;
                  }
              }
              """
          )
        );
    }

    @Test
    void reOrderQueryArgsNoChange() {
        //language=java
        rewriteRun(
          user,
          java(
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;
              import java.util.List;

              class MyDao {

                  JdbcTemplate jdbcTemplate;

                  List<User> getUserAgesByNameHasNull(String first, String last) {
                      Object[] args = new String[]{first, last};
                      return jdbcTemplate.queryForList("select NAME, AGE from USER where NAME = ?", null, User.class);
                  }

                  List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? AND LAST = ?", (resultSet, i) -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      }, args);
                  }

                  User getUserByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? && LAST = ?", resultSet -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      }, args);
                  }

                  User getUserAge2(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      final User user = new User();
                      jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? && LAST = ?", rs -> {
                          user.setAge = rs.getInt("AGE");
                          user.setName = rs.getString("NAME");
                      }, args);
                      return user;
                  }
              }
              """
          )
        );
    }

    @Test
    void reOrderQueryForList() {
        //language=java
        rewriteRun(
          user,
          java(
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;

              import java.util.List;

              class MyDao {

                  JdbcTemplate jdbcTemplate;

                  List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new String[]{first, last};
                      return jdbcTemplate.queryForList("select NAME, AGE from USER where NAME = ?", args, User.class);
                  }
              }
              """,
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;

              import java.util.List;

              class MyDao {

                  JdbcTemplate jdbcTemplate;

                  List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new String[]{first, last};
                      return jdbcTemplate.queryForList("select NAME, AGE from USER where NAME = ?", User.class, args);
                  }
              }
              """
          )
        );
    }

    @Test
    void queryForListNoChange() {
        //language=java
        rewriteRun(
          user,
          java(
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;

              import java.util.List;

              class MyDao {

                  JdbcTemplate jdbcTemplate;

                  List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new String[]{first, last};
                      return jdbcTemplate.queryForList("select NAME, AGE from USER where NAME = ?", User.class, args);
                  }
              }
              """
          )
        );
    }
}

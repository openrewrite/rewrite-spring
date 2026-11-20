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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                          user.setAge(rs.getInt("AGE"));
                          user.setName(rs.getString("NAME"));
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
                          user.setAge(rs.getInt("AGE"));
                          user.setName(rs.getString("NAME"));
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
                          user.setAge(rs.getInt("AGE"));
                          user.setName(rs.getString("NAME"));
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

    @Issue("https://github.com/openrewrite/rewrite-spring/pull/885")
    @Test
    void inlineArrayInitializerValues() {
        //language=java
        rewriteRun(
          user,
          java(
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;

              class MyDao {
                  JdbcTemplate jdbcTemplate;

                  User getUser(String first, String last) {
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", new Object[]{ first, last }, User.class);
                  }

                  User getUserWithoutSpaces(String first, String last) {
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", new Object[]{first, last}, User.class);
                  }
              }
              """,
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;

              class MyDao {
                  JdbcTemplate jdbcTemplate;

                  User getUser(String first, String last) {
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", User.class, first, last);
                  }

                  User getUserWithoutSpaces(String first, String last) {
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", User.class, first, last);
                  }
              }
              """
          )
        );
    }


    @Test
    void methodTypeFollowsVarArgsOverload() {
        //language=java
        rewriteRun(
          user,
          java(
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;

              class MyDao {
                  JdbcTemplate jdbcTemplate;

                  User getUser(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", args, User.class);
                  }
              }
              """,
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;

              class MyDao {
                  JdbcTemplate jdbcTemplate;

                  User getUser(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", User.class, args);
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> new JavaIsoVisitor<Integer>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer ctx) {
                    if ("queryForObject".equals(method.getSimpleName())) {
                        List<JavaType> parameterTypes = method.getMethodType().getParameterTypes();
                        assertThat(parameterTypes).hasSize(3);
                        assertThat(TypeUtils.asFullyQualified(parameterTypes.get(1)).getFullyQualifiedName()).isEqualTo("java.lang.Class");
                        assertThat(parameterTypes.get(2)).isInstanceOf(JavaType.Array.class);
                    }
                    return method;
                }
            }.visit(cu, 0))
          )
        );
    }
}

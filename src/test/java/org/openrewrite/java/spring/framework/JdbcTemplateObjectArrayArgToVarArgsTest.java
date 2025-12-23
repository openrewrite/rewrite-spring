/*
 * Copyright 2025 the original author or authors.
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
            .classpathFromResources(
              new InMemoryExecutionContext(),
              "spring-jdbc-4.1.+",
              "spring-tx-4.1.+",
              "spring-beans-5.+",
              "spring-core-5.+"
            ));
    }

    //language=java
    private final SourceSpecs user = java(
      """
        package abc;

        public class User {
            private String name;
            private Integer age;

            public Integer getAge() {
                return age;
            }

            public void setAge(Integer age) {
                this.age = age;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }
        """
    );

    @DocumentExample
    @Test
    void reorderQueryForObjectArgsAndFlattenArray() {
        rewriteRun(
          user,
          java(
            """
              package abc;

              import org.springframework.jdbc.core.JdbcTemplate;

              public class MyDao {

                  final JdbcTemplate jdbcTemplate;

                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }

                  public User getUser(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.queryForObject(
                          "select NAME, AGE from USER where FIRST = ? and LAST = ?",
                          User.class,
                          args
                      );
                  }
              }
              """
          )
        );

    }

    @Test
    void reorderQueryWithRowMapperAndFlattenArray() {
        rewriteRun(
          user,
          java(
            """
              package abc;

              import org.springframework.jdbc.core.JdbcTemplate;
              import java.util.List;

              public class MyDao {

                  final JdbcTemplate jdbcTemplate;

                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }

                  public List<User> getUsers(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query(
                          "select NAME, AGE from USER where FIRST = ? and LAST = ?",
                          (rs, i) -> {
                              User user = new User();
                              user.setName(rs.getString("NAME"));
                              user.setAge(rs.getInt("AGE"));
                              return user;
                          },
                          args
                      );
                  }
              }
              """
          )
        );

    }

    @Test
    void reorderQueryForListAndFlattenArray() {
        rewriteRun(
          user,
          java(
            """
              package abc;

              import org.springframework.jdbc.core.JdbcTemplate;
              import java.util.List;

              public class MyDao {

                  final JdbcTemplate jdbcTemplate;

                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }

                  public List<User> getUsers(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.queryForList(
                          "select NAME, AGE from USER where FIRST = ? and LAST = ?",
                          User.class,
                          args
                      );
                  }
              }
              """
          )
        );

    }

    @Test
    void doesNotChangeAlreadyVarArgsUsage() {
        rewriteRun(
          user,
          java(
            """
                package abc;

                 import org.springframework.jdbc.core.JdbcTemplate;
                 import java.util.List;

                 public class MyDao {

                     final JdbcTemplate jdbcTemplate;

                     public MyDao(JdbcTemplate jdbcTemplate) {
                         this.jdbcTemplate = jdbcTemplate;
                     }

                     public List<User> getUsers(String first, String last) {
                         return jdbcTemplate.query(
                             "select NAME, AGE from USER where FIRST = ? and LAST = ?",
                             (rs, i) -> {
                                 User user = new User();
                                 user.setName(rs.getString("NAME"));
                                 user.setAge(rs.getInt("AGE"));
                                 return user;
                             },
                             first,
                             last
                         );
                     }
                 }

              """
          )
        );
    }
}

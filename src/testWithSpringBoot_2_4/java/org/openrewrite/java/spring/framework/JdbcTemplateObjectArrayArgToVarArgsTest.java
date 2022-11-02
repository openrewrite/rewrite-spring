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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
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
            .classpath("spring-jdbc", "spring-tx", "spring-beans", "spring-core"));
    }

    //language=java
    private final SourceSpecs user = java(
      """
        package abc;
        public class User {
            private String name;
            private Integer Age;
            
            public Integer getAge() {
                return Age;
            }
            public void setAge(Integer age) {
                Age = age;
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
                            
              public class MyDao {
                            
                  final JdbcTemplate jdbcTemplate;
                  
                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }
                  
                  public User getUser(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", args, User.class);
                  }
                  
                  public User getUser2(String first, String last) {
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
                            
              public class MyDao {
                            
                  final JdbcTemplate jdbcTemplate;
                  
                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }
                  
                  public User getUser(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.queryForObject("select NAME, AGE from USER where FIRST = ? && LAST = ?", User.class, args);
                  }
                  
                  public User getUser2(String first, String last) {
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
                            
              public class MyDao {
                            
                  final JdbcTemplate jdbcTemplate;
                  
                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }
                  
                  public List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? AND LAST = ?", args, (resultSet, i) -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      });
                  }
                  
                  public User getUserByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? && LAST = ?", args, resultSet -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      });
                  }
                  
                  public User getUserAge2(String first, String last) {
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
                            
              public class MyDao {
                            
                  final JdbcTemplate jdbcTemplate;
                  
                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }
                  
                  public List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? AND LAST = ?", (resultSet, i) -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      }, args);
                  }
                  
                  public User getUserByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? && LAST = ?", resultSet -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      }, args);
                  }
                  
                  public User getUserAge2(String first, String last) {
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
                            
              public class MyDao {
                            
                  final JdbcTemplate jdbcTemplate;
                  
                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }
                  
                  public List<User> getUserAgesByNameHasNull(String first, String last) {
                      Object[] args = new String[]{first, last};
                      return jdbcTemplate.queryForList("select NAME, AGE from USER where NAME = ?", null, User.class);
                  }
                  
                  public List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? AND LAST = ?", (resultSet, i) -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      }, args);
                  }
                  
                  public User getUserByName(String first, String last) {
                      Object[] args = new Object[]{first, last};
                      return jdbcTemplate.query("select NAME, AGE from USER where FIRST = ? && LAST = ?", resultSet -> {
                          User user = new User();
                          user.setName(resultSet.getString("NAME"));
                          user.setAge(resultSet.getInt("AGE"));
                          return user;
                      }, args);
                  }
                  
                  public User getUserAge2(String first, String last) {
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
                            
              public class MyDao {
                            
                  final JdbcTemplate jdbcTemplate;
                            
                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }
                            
                  public List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new String[]{first, last};
                      return jdbcTemplate.queryForList("select NAME, AGE from USER where NAME = ?", args, User.class);
                  }
              }
              """,
            """
              package abc;
              import org.springframework.jdbc.core.JdbcTemplate;

              import java.util.List;
                            
              public class MyDao {
                            
                  final JdbcTemplate jdbcTemplate;
                            
                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }
                            
                  public List<User> getUserAgesByName(String first, String last) {
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
                            
              public class MyDao {
                            
                  final JdbcTemplate jdbcTemplate;
                            
                  public MyDao(JdbcTemplate jdbcTemplate) {
                      this.jdbcTemplate = jdbcTemplate;
                  }
                            
                  public List<User> getUserAgesByName(String first, String last) {
                      Object[] args = new String[]{first, last};
                      return jdbcTemplate.queryForList("select NAME, AGE from USER where NAME = ?", User.class, args);
                  }
              }
              """
          )
        );
    }
}

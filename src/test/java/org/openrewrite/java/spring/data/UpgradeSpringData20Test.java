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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeSpringData20Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.data.UpgradeSpringData_2_0")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13")
            .dependsOn(
              "package com.querydsl.core.types; public interface Predicate {}",
              "package com.querydsl.core.types; public class OrderSpecifier<T extends Comparable> {}",
              //language=java
              """
                package org.springframework.data.jpa.repository.support;

                import java.io.Serializable;

                public abstract class QueryDslJpaRepository<T, ID extends Serializable> {
                }
                """,
              "package org.springframework.data.jpa.repository.support; public abstract class QueryDslRepositorySupport {}",
              //language=java
              """
                package org.springframework.data.mongodb.repository.support;

                import java.io.Serializable;

                public abstract class QueryDslMongoRepository<T, ID extends Serializable> {
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void querydslPredicateExecutor() {
        rewriteRun(
          java(
            """
              import com.querydsl.core.types.Predicate;
              import org.springframework.data.querydsl.QueryDslPredicateExecutor;
              import org.springframework.data.repository.CrudRepository;

              class User {
              }

              interface UserRepository extends CrudRepository<User, Long>, QueryDslPredicateExecutor<User> {
              }

              class UserService {
                  UserRepository users;

                  User find(Predicate predicate, Long id) {
                      User found = users.findOne(predicate);
                      return found == null ? users.findOne(id) : found;
                  }
              }
              """,
            """
              import com.querydsl.core.types.Predicate;
              import org.springframework.data.querydsl.QuerydslPredicateExecutor;
              import org.springframework.data.repository.CrudRepository;

              class User {
              }

              interface UserRepository extends CrudRepository<User, Long>, QuerydslPredicateExecutor<User> {
              }

              class UserService {
                  UserRepository users;

                  User find(Predicate predicate, Long id) {
                      User found = users.findOne(predicate).orElse(null);
                      return found == null ? users.findById(id).orElse(null) : found;
                  }
              }
              """
          )
        );
    }

    @Test
    void querydslSupportClasses() {
        rewriteRun(
          java(
            """
              import org.springframework.data.jpa.repository.support.QueryDslJpaRepository;
              import org.springframework.data.jpa.repository.support.QueryDslRepositorySupport;
              import org.springframework.data.mongodb.repository.support.QueryDslMongoRepository;
              import org.springframework.data.querydsl.QueryDslUtils;

              abstract class JpaBase<T> extends QueryDslJpaRepository<T, Long> {
              }

              abstract class MongoBase<T> extends QueryDslMongoRepository<T, String> {
              }

              abstract class Support extends QueryDslRepositorySupport {
                  boolean present = QueryDslUtils.QUERY_DSL_PRESENT;
              }
              """,
            """
              import org.springframework.data.jpa.repository.support.QuerydslJpaRepository;
              import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
              import org.springframework.data.mongodb.repository.support.QuerydslMongoRepository;
              import org.springframework.data.querydsl.QuerydslUtils;

              abstract class JpaBase<T> extends QuerydslJpaRepository<T, Long> {
              }

              abstract class MongoBase<T> extends QuerydslMongoRepository<T, String> {
              }

              abstract class Support extends QuerydslRepositorySupport {
                  boolean present = QuerydslUtils.QUERY_DSL_PRESENT;
              }
              """
          )
        );
    }

    @Test
    void ownClassesKeepTheirNames() {
        rewriteRun(
          java(
            """
              class CustomQueryDslRepository {
              }
              """
          )
        );
    }
}

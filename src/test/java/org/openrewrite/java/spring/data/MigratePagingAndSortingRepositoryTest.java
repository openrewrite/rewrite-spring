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

class MigratePagingAndSortingRepositoryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigratePagingAndSortingRepository())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-data-commons-2.*", "spring-data-jpa-2.*"));
    }

    @DocumentExample
    @Test
    void addsCrudRepositoryWhenOnlyPagingAndSorting() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.repository.PagingAndSortingRepository;

              public interface UserRepository extends PagingAndSortingRepository<User, Long> {
              }
              """,
            """
              import org.springframework.data.repository.CrudRepository;
              import org.springframework.data.repository.PagingAndSortingRepository;

              public interface UserRepository extends PagingAndSortingRepository<User, Long>, CrudRepository<User, Long> {
              }
              """
          ),
          //language=java
          java(
            """
              public class User {
                  private Long id;
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenAlreadyExtendsCrudRepository() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.repository.CrudRepository;
              import org.springframework.data.repository.PagingAndSortingRepository;

              public interface UserRepository extends PagingAndSortingRepository<User, Long>, CrudRepository<User, Long> {
              }
              """
          ),
          //language=java
          java(
            """
              public class User {
                  private Long id;
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenExtendsJpaRepository() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.jpa.repository.JpaRepository;

              public interface UserRepository extends JpaRepository<User, Long> {
              }
              """
          ),
          //language=java
          java(
            """
              public class User {
                  private Long id;
              }
              """
          )
        );
    }

    @Test
    void addsCrudRepositoryWithCustomInterface() {
        //language=java
        rewriteRun(
          java(
            """
              public interface CustomRepository<T> {
                  T findByName(String name);
              }
              """
          ),
          java(
            """
              import org.springframework.data.repository.PagingAndSortingRepository;

              public interface UserRepository extends PagingAndSortingRepository<User, Long>, CustomRepository<User> {
              }
              """,
            """
              import org.springframework.data.repository.CrudRepository;
              import org.springframework.data.repository.PagingAndSortingRepository;

              public interface UserRepository extends PagingAndSortingRepository<User, Long>, CustomRepository<User>, CrudRepository<User, Long> {
              }
              """
          ),
          //language=java
          java(
            """
              public class User {
                  private Long id;
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenTransitivelyExtendsCrudRepository() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.jpa.repository.JpaRepository;

              public interface BaseRepository extends JpaRepository<User, Long> {
              }
              """
          ),
          java(
            """
              import org.springframework.data.repository.PagingAndSortingRepository;

              public interface UserRepository extends PagingAndSortingRepository<User, Long>, BaseRepository {
              }
              """
          ),
          //language=java
          java(
            """
              public class User {
                  private Long id;
              }
              """
          )
        );
    }

    @Test
    void noChangeForClass() {
        //language=java
        rewriteRun(
          java(
            """
              public class NotAnInterface {
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenAlreadyOnSpringData3() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-data-commons-3.*")),
          //language=java
          java(
            """
              import org.springframework.data.repository.PagingAndSortingRepository;

              public interface UserRepository extends PagingAndSortingRepository<User, Long> {
              }
              """
          ),
          //language=java
          java(
            """
              public class User {
                  private Long id;
              }
              """
          )
        );
    }
}

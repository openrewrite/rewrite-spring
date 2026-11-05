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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.java.Assertions.java;

class MigrateCrudRepositoryMethodsTest implements RewriteTest {

    private static final SourceSpecs USER = java(
      """
        package com.acme;

        public class User implements java.io.Serializable {
            public String getName() {
                return "";
            }
        }
        """
    );

    private static final SourceSpecs USER_REPOSITORY = java(
      """
        package com.acme;

        import org.springframework.data.repository.CrudRepository;

        public interface UserRepository extends CrudRepository<User, Long> {
        }
        """
    );

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateCrudRepositoryMethods())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-data-commons-1.13", "mockito-core"));
    }

    @DocumentExample
    @Test
    void renamesEveryMethodSpringData2Renamed() {
        rewriteRun(
          USER,
          USER_REPOSITORY,
          java(
            """
              import com.acme.User;
              import com.acme.UserRepository;
              import java.util.List;

              class UserService {
                  UserRepository users;

                  User find(Long id) {
                      return users.findOne(id);
                  }

                  boolean has(Long id) {
                      return users.exists(id);
                  }

                  void remove(Long id) {
                      users.delete(id);
                  }

                  void removeAll(List<User> all) {
                      users.delete(all);
                  }

                  Iterable<User> store(List<User> all) {
                      return users.save(all);
                  }

                  Iterable<User> load(List<Long> ids) {
                      return users.findAll(ids);
                  }
              }
              """,
            """
              import com.acme.User;
              import com.acme.UserRepository;
              import java.util.List;

              class UserService {
                  UserRepository users;

                  User find(Long id) {
                      return users.findById(id).orElse(null);
                  }

                  boolean has(Long id) {
                      return users.existsById(id);
                  }

                  void remove(Long id) {
                      users.deleteById(id);
                  }

                  void removeAll(List<User> all) {
                      users.deleteAll(all);
                  }

                  Iterable<User> store(List<User> all) {
                      return users.saveAll(all);
                  }

                  Iterable<User> load(List<Long> ids) {
                      return users.findAllById(ids);
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0",
      "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7"
    })
    void chainedFromTheSpringBootUpgrades(String recipe) {
        rewriteRun(
          spec -> spec.recipeFromResources(recipe),
          USER,
          USER_REPOSITORY,
          java(
            """
              import com.acme.User;
              import com.acme.UserRepository;

              class UserService {
                  UserRepository users;

                  User find(Long id) {
                      return users.findOne(id);
                  }

                  void remove(Long id) {
                      users.delete(id);
                  }
              }
              """,
            """
              import com.acme.User;
              import com.acme.UserRepository;

              class UserService {
                  UserRepository users;

                  User find(Long id) {
                      return users.findById(id).orElse(null);
                  }

                  void remove(Long id) {
                      users.deleteById(id);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsMethodsSpringData2DidNotRename() {
        rewriteRun(
          USER,
          USER_REPOSITORY,
          java(
            """
              import com.acme.User;
              import com.acme.UserRepository;

              class UserService {
                  UserRepository users;

                  User store(User user) {
                      return users.save(user);
                  }

                  void remove(User user) {
                      users.delete(user);
                  }

                  void clear() {
                      users.deleteAll();
                  }

                  Iterable<User> all() {
                      return users.findAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void entityThatIsIterableIsNotACollectionOfEntities() {
        rewriteRun(
          java(
            """
              import org.springframework.data.repository.CrudRepository;
              import java.util.Collections;
              import java.util.Iterator;

              class Cart implements Iterable<String> {
                  public Iterator<String> iterator() {
                      return Collections.emptyIterator();
                  }
              }

              interface CartRepository extends CrudRepository<Cart, Long> {
              }

              class CartService {
                  CartRepository carts;

                  void store(Cart cart) {
                      carts.save(cart);
                      carts.delete(cart);
                  }
              }
              """
          )
        );
    }

    @Test
    void primitiveArgumentIsBoxedToTheId() {
        rewriteRun(
          USER,
          USER_REPOSITORY,
          java(
            """
              import com.acme.UserRepository;

              class UserService {
                  UserRepository users;

                  void remove(long id) {
                      users.delete(id);
                  }
              }
              """,
            """
              import com.acme.UserRepository;

              class UserService {
                  UserRepository users;

                  void remove(long id) {
                      users.deleteById(id);
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedAndNestedCalls() {
        rewriteRun(
          USER,
          USER_REPOSITORY,
          java(
            """
              import com.acme.User;
              import com.acme.UserRepository;

              class UserService {
                  UserRepository users;

                  String name(Long id) {
                      return users.findOne(id).getName();
                  }

                  String multiline(Long id) {
                      return users
                          .findOne(id)
                          .getName();
                  }

                  boolean missing(Long id) {
                      return users.findOne(id) == null;
                  }

                  void log(Long id) {
                      System.out.println(users.findOne(users.findOne(id).hashCode() + 0L));
                  }
              }
              """,
            """
              import com.acme.User;
              import com.acme.UserRepository;

              class UserService {
                  UserRepository users;

                  String name(Long id) {
                      return users.findById(id).orElse(null).getName();
                  }

                  String multiline(Long id) {
                      return users
                          .findById(id).orElse(null)
                          .getName();
                  }

                  boolean missing(Long id) {
                      return users.findById(id).orElse(null) == null;
                  }

                  void log(Long id) {
                      System.out.println(users.findById(users.findById(id).orElse(null).hashCode() + 0L).orElse(null));
                  }
              }
              """
          )
        );
    }

    @Test
    void optionalOfNullableIsNoLongerNeeded() {
        rewriteRun(
          USER,
          USER_REPOSITORY,
          java(
            """
              import com.acme.User;
              import com.acme.UserRepository;
              import java.util.Optional;

              class UserService {
                  UserRepository users;

                  Optional<User> find(Long id) {
                      return Optional.ofNullable(users.findOne(id));
                  }

                  String name(Long id) {
                      return Optional.ofNullable(users.findOne(id)).map(User::getName).orElse("");
                  }
              }
              """,
            """
              import com.acme.User;
              import com.acme.UserRepository;
              import java.util.Optional;

              class UserService {
                  UserRepository users;

                  Optional<User> find(Long id) {
                      return users.findById(id);
                  }

                  String name(Long id) {
                      return users.findById(id).map(User::getName).orElse("");
                  }
              }
              """
          )
        );
    }

    @Test
    void jpaRepositoryRedeclaresTheIterableMethods() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13")
            .dependsOn(
              //language=java
              """
                package org.springframework.data.jpa.repository;

                import org.springframework.data.repository.PagingAndSortingRepository;

                import java.io.Serializable;
                import java.util.List;

                public interface JpaRepository<T, ID extends Serializable> extends PagingAndSortingRepository<T, ID> {
                    List<T> findAll(Iterable<ID> ids);
                    <S extends T> List<S> save(Iterable<S> entities);
                    T getOne(ID id);
                }
                """
            )),
          USER,
          java(
            """
              import com.acme.User;
              import org.springframework.data.jpa.repository.JpaRepository;
              import java.util.List;

              interface UserRepository extends JpaRepository<User, Long> {
              }

              class UserService {
                  UserRepository users;

                  List<User> load(List<Long> ids) {
                      return users.findAll(ids);
                  }

                  List<User> store(List<User> all) {
                      return users.save(all);
                  }

                  User find(Long id) {
                      return users.findOne(id);
                  }

                  User reference(Long id) {
                      return users.getOne(id);
                  }
              }
              """,
            """
              import com.acme.User;
              import org.springframework.data.jpa.repository.JpaRepository;
              import java.util.List;

              interface UserRepository extends JpaRepository<User, Long> {
              }

              class UserService {
                  UserRepository users;

                  List<User> load(List<Long> ids) {
                      return users.findAllById(ids);
                  }

                  List<User> store(List<User> all) {
                      return users.saveAll(all);
                  }

                  User find(Long id) {
                      return users.findById(id).orElse(null);
                  }

                  User reference(Long id) {
                      return users.getOne(id);
                  }
              }
              """
          )
        );
    }

    @Nested
    class TypeHierarchy {

        @Test
        void idResolvedThroughIntermediateGenericRepository() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;
                  import java.io.Serializable;

                  interface BaseRepository<E, K extends Serializable> extends CrudRepository<E, K> {
                  }

                  interface UserRepository extends BaseRepository<User, Long> {
                  }

                  class UserService {
                      UserRepository users;

                      void remove(Long id, User user) {
                          users.delete(id);
                          users.delete(user);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;
                  import java.io.Serializable;

                  interface BaseRepository<E, K extends Serializable> extends CrudRepository<E, K> {
                  }

                  interface UserRepository extends BaseRepository<User, Long> {
                  }

                  class UserService {
                      UserRepository users;

                      void remove(Long id, User user) {
                          users.deleteById(id);
                          users.delete(user);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void typeVariablesOfAGenericService() {
            rewriteRun(
              java(
                """
                  import org.springframework.data.repository.CrudRepository;
                  import java.io.Serializable;
                  import java.util.List;

                  abstract class BaseService<E, K extends Serializable, R extends CrudRepository<E, K>> {
                      CrudRepository<E, K> repository;
                      R typed;

                      E find(K id) {
                          return repository.findOne(id);
                      }

                      void remove(K id, E entity, List<E> entities) {
                          repository.delete(id);
                          repository.delete(entity);
                          repository.delete(entities);
                          typed.delete(id);
                          typed.delete(entity);
                      }
                  }
                  """,
                """
                  import org.springframework.data.repository.CrudRepository;
                  import java.io.Serializable;
                  import java.util.List;

                  abstract class BaseService<E, K extends Serializable, R extends CrudRepository<E, K>> {
                      CrudRepository<E, K> repository;
                      R typed;

                      E find(K id) {
                          return repository.findById(id).orElse(null);
                      }

                      void remove(K id, E entity, List<E> entities) {
                          repository.deleteById(id);
                          repository.delete(entity);
                          repository.deleteAll(entities);
                          typed.deleteById(id);
                          typed.delete(entity);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void rawRepository() {
            rewriteRun(
              java(
                """
                  import org.springframework.data.repository.CrudRepository;

                  class RawService {
                      CrudRepository repository;

                      Object find(Long id) {
                          return repository.findOne(id);
                      }

                      void remove(Long id, Object entity) {
                          repository.delete(id);
                          repository.delete(entity);
                      }
                  }
                  """,
                """
                  import org.springframework.data.repository.CrudRepository;

                  class RawService {
                      CrudRepository repository;

                      Object find(Long id) {
                          return repository.findById(id).orElse(null);
                      }

                      void remove(Long id, Object entity) {
                          repository.deleteById(id);
                          repository.delete(entity);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void unqualifiedCallInADefaultMethod() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long> {
                      default boolean missing(Long id) {
                          return findOne(id) == null;
                      }

                      default void remove(Long id) {
                          delete(id);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long> {
                      default boolean missing(Long id) {
                          return findById(id).orElse(null) == null;
                      }

                      default void remove(Long id) {
                          deleteById(id);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class MethodReferences {

        @Test
        void renamesAlongWithTheCalls() {
            rewriteRun(
              USER,
              USER_REPOSITORY,
              java(
                """
                  import com.acme.User;
                  import com.acme.UserRepository;
                  import java.util.List;
                  import java.util.Optional;
                  import java.util.function.Predicate;

                  class UserService {
                      UserRepository users;

                      void removeByIds(List<Long> ids) {
                          ids.forEach(users::delete);
                          ids.forEach(this.users::delete);
                      }

                      void removeBatches(List<List<User>> batches) {
                          batches.forEach(users::delete);
                          batches.forEach(users::save);
                      }

                      Predicate<Long> known() {
                          return users::exists;
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import com.acme.UserRepository;
                  import java.util.List;
                  import java.util.Optional;
                  import java.util.function.Predicate;

                  class UserService {
                      UserRepository users;

                      void removeByIds(List<Long> ids) {
                          ids.forEach(users::deleteById);
                          ids.forEach(this.users::deleteById);
                      }

                      void removeBatches(List<List<User>> batches) {
                          batches.forEach(users::deleteAll);
                          batches.forEach(users::saveAll);
                      }

                      Predicate<Long> known() {
                          return users::existsById;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void keepsTheEntityOverloads() {
            rewriteRun(
              USER,
              USER_REPOSITORY,
              java(
                """
                  import com.acme.User;
                  import com.acme.UserRepository;
                  import java.util.Optional;

                  class UserService {
                      UserRepository users;

                      void remove(User user) {
                          Optional.ofNullable(user).ifPresent(users::delete);
                          Optional.ofNullable(user).ifPresent(users::save);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void findOneIsLeftForTheCompilerToPointOut() {
            rewriteRun(
              USER,
              USER_REPOSITORY,
              java(
                """
                  import com.acme.User;
                  import com.acme.UserRepository;
                  import java.util.List;
                  import java.util.stream.Stream;

                  class UserService {
                      UserRepository users;

                      Stream<User> load(List<Long> ids) {
                          return ids.stream().map(users::findOne);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Declarations {

        @Test
        void redeclaredByARepositoryWithItsCallers() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;
                  import java.util.List;

                  interface UserRepository extends CrudRepository<User, Long> {
                      @Override
                      User findOne(Long id);

                      @Override
                      boolean exists(Long id);

                      @Deprecated
                      @Override
                      void delete(Long id);

                      @Override
                      void delete(User user);

                      @Override
                      List<User> findAll(Iterable<Long> ids);

                      @Override
                      <S extends User> List<S> save(Iterable<S> users);

                      @Override
                      <S extends User> S save(S user);
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id) {
                          users.delete(id);
                          return users.findOne(id);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;
                  import java.util.List;
                  import java.util.Optional;

                  interface UserRepository extends CrudRepository<User, Long> {
                      @Override
                      Optional<User> findById(Long id);

                      @Override
                      boolean existsById(Long id);

                      @Deprecated
                      @Override
                      void deleteById(Long id);

                      @Override
                      void delete(User user);

                      @Override
                      List<User> findAllById(Iterable<Long> ids);

                      @Override
                      <S extends User> List<S> saveAll(Iterable<S> users);

                      @Override
                      <S extends User> S save(S user);
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id) {
                          users.deleteById(id);
                          return users.findById(id).orElse(null);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void redeclaredWithTheTypeVariablesOfABaseRepository() {
            rewriteRun(
              java(
                """
                  import org.springframework.data.repository.CrudRepository;
                  import java.io.Serializable;

                  interface BaseRepository<E, K extends Serializable> extends CrudRepository<E, K> {
                      E findOne(K id);
                  }
                  """,
                """
                  import org.springframework.data.repository.CrudRepository;
                  import java.io.Serializable;
                  import java.util.Optional;

                  interface BaseRepository<E, K extends Serializable> extends CrudRepository<E, K> {
                      Optional<E> findById(K id);
                  }
                  """
              )
            );
        }

        @Test
        void ownQueryMethodsAreLeftAlone() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.domain.Sort;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long> {
                      User findOne(Sort sort);
                      User findOneByName(String name);
                  }
                  """
              )
            );
        }

        @Test
        void overrideOfFindOneIsLeftWithItsCallers() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  abstract class InMemoryUsers implements CrudRepository<User, Long> {
                      @Override
                      public User findOne(Long id) {
                          return null;
                      }

                      User require(Long id) {
                          return this.findOne(id);
                      }
                  }

                  class UserService {
                      InMemoryUsers users;

                      User find(Long id) {
                          return users.findOne(id);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void defaultMethodOverridingFindOneIsLeftWithItsCallers() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long> {
                      @Override
                      default User findOne(Long id) {
                          return null;
                      }
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id) {
                          return users.findOne(id);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void callingSpringDataImplementationFromASubclass() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13")
                .dependsOn(
                  //language=java
                  """
                    package org.springframework.data.jpa.repository.support;

                    import org.springframework.data.repository.CrudRepository;

                    import java.io.Serializable;

                    public abstract class SimpleJpaRepository<T, ID extends Serializable> implements CrudRepository<T, ID> {
                        @Override
                        public T findOne(ID id) {
                            return null;
                        }
                    }
                    """
                )),
              java(
                """
                  import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
                  import java.io.Serializable;

                  abstract class AuditedRepository<E, K extends Serializable> extends SimpleJpaRepository<E, K> {
                      E audited(K id) {
                          return super.findOne(id);
                      }
                  }
                  """,
                """
                  import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
                  import java.io.Serializable;

                  abstract class AuditedRepository<E, K extends Serializable> extends SimpleJpaRepository<E, K> {
                      E audited(K id) {
                          return super.findById(id).orElse(null);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void implementationKeepsOverriding() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  abstract class InMemoryUsers implements CrudRepository<User, Long> {
                      @Override
                      public boolean exists(Long id) {
                          return false;
                      }

                      @Override
                      public void delete(Long id) {
                      }

                      @Override
                      public void delete(User user) {
                          delete(1L);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  abstract class InMemoryUsers implements CrudRepository<User, Long> {
                      @Override
                      public boolean existsById(Long id) {
                          return false;
                      }

                      @Override
                      public void deleteById(Long id) {
                      }

                      @Override
                      public void delete(User user) {
                          deleteById(1L);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class RepositoryAlreadyHasFindById {

        @Test
        void returningTheEntityBecomesTheMethodSpringData2Declares() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  import static org.mockito.Mockito.when;

                  interface UserRepository extends CrudRepository<User, Long> {
                      User findById(Long id);
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id) {
                          User found = users.findById(id);
                          return found == null ? users.findOne(id) : found;
                      }

                      void stub(User user) {
                          when(users.findById(1L)).thenReturn(user);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  import java.util.Optional;

                  import static org.mockito.Mockito.when;

                  interface UserRepository extends CrudRepository<User, Long> {
                      Optional<User> findById(Long id);
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id) {
                          User found = users.findById(id).orElse(null);
                          return found == null ? users.findById(id).orElse(null) : found;
                      }

                      void stub(User user) {
                          when(users.findById(1L)).thenReturn(Optional.ofNullable(user));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void verificationNeedsNoChange() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long> {
                      java.util.Optional<User> findById(Long id);
                  }
                  """
              ),
              java(
                """
                  import org.mockito.Mockito;

                  class UserServiceTest {
                      UserRepository users;

                      void check() {
                          Mockito.verify(users).findById(1L);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void verificationOfOneThatBecomesThatMethodStaysAsItIs() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long> {
                      User findById(Long id);
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  import java.util.Optional;

                  interface UserRepository extends CrudRepository<User, Long> {
                      Optional<User> findById(Long id);
                  }
                  """
              ),
              java(
                """
                  import org.mockito.Mockito;

                  class UserServiceTest {
                      UserRepository users;

                      void check() {
                          Mockito.verify(users).findById(1L);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void returningAnOptionalAlreadyIsThatMethod() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;
                  import java.util.Optional;

                  interface UserRepository extends CrudRepository<User, Long> {
                      Optional<User> findById(Long id);
                  }

                  class UserService {
                      UserRepository users;

                      Optional<User> maybe(Long id) {
                          return users.findById(id);
                      }

                      User find(Long id) {
                          return users.findOne(id);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;
                  import java.util.Optional;

                  interface UserRepository extends CrudRepository<User, Long> {
                      Optional<User> findById(Long id);
                  }

                  class UserService {
                      UserRepository users;

                      Optional<User> maybe(Long id) {
                          return users.findById(id);
                      }

                      User find(Long id) {
                          return users.findById(id).orElse(null);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void returningSomethingElseLeavesFindOneToAPerson() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;
                  import java.util.List;

                  interface UserRepository extends CrudRepository<User, Long> {
                      List<User> findById(Long id);
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id) {
                          return users.findOne(id);
                      }

                      List<User> all(Long id) {
                          return users.findById(id);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void inheritedFromABaseRepository() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;
                  import java.util.List;

                  interface BaseUserRepository extends CrudRepository<User, Long> {
                      List<User> findById(Long id);
                  }

                  interface UserRepository extends BaseUserRepository {
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id) {
                          return users.findOne(id);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void overloadForAPrimitiveWouldCaptureTheCall() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long> {
                      @Override
                      User findOne(Long id);

                      User findById(long id);
                  }

                  class UserService {
                      UserRepository users;

                      User find(long id) {
                          return users.findOne(id);
                      }
                  }
                  """
              )
            );
        }
    }

    /**
     * `findOne` of a query keeps its name on Spring Data 2.x and only starts to return an `Optional`.
     */
    @Nested
    class FindOneOfAQuery {

        private static final String JPA_SPECIFICATION_EXECUTOR =
          //language=java
          """
            package org.springframework.data.jpa.repository;

            import org.springframework.data.jpa.domain.Specification;

            public interface JpaSpecificationExecutor<T> {
                T findOne(Specification<T> spec);
            }
            """;

        private static final String SPECIFICATION =
          "package org.springframework.data.jpa.domain; public interface Specification<T> {}";

        private static final String QUERYDSL_PREDICATE =
          "package com.querydsl.core.types; public interface Predicate {}";

        private static final String QUERYDSL_ORDER_SPECIFIER =
          "package com.querydsl.core.types; public class OrderSpecifier<T extends Comparable> {}";

        @Test
        void bySpecification() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13", "mockito-core")
                .dependsOn(SPECIFICATION, JPA_SPECIFICATION_EXECUTOR)),
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.jpa.domain.Specification;
                  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                  import org.springframework.data.repository.Repository;
                  import java.util.Optional;

                  import static org.mockito.Mockito.verify;
                  import static org.mockito.Mockito.when;

                  interface UserRepository extends Repository<User, Long>, JpaSpecificationExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      User find(Specification<User> spec) {
                          return users.findOne(spec);
                      }

                      Optional<User> maybe(Specification<User> spec) {
                          return Optional.ofNullable(users.findOne(spec));
                      }

                      void stub(Specification<User> spec, User user) {
                          when(users.findOne(spec)).thenReturn(user);
                          verify(users).findOne(spec);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.jpa.domain.Specification;
                  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                  import org.springframework.data.repository.Repository;
                  import java.util.Optional;

                  import static org.mockito.Mockito.verify;
                  import static org.mockito.Mockito.when;

                  interface UserRepository extends Repository<User, Long>, JpaSpecificationExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      User find(Specification<User> spec) {
                          return users.findOne(spec).orElse(null);
                      }

                      Optional<User> maybe(Specification<User> spec) {
                          return users.findOne(spec);
                      }

                      void stub(Specification<User> spec, User user) {
                          when(users.findOne(spec)).thenReturn(Optional.ofNullable(user));
                          verify(users).findOne(spec);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void byExample() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.domain.Example;
                  import org.springframework.data.repository.CrudRepository;
                  import org.springframework.data.repository.query.QueryByExampleExecutor;

                  interface UserRepository extends CrudRepository<User, Long>, QueryByExampleExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      User find(Example<User> example) {
                          return users.findOne(example);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.domain.Example;
                  import org.springframework.data.repository.CrudRepository;
                  import org.springframework.data.repository.query.QueryByExampleExecutor;

                  interface UserRepository extends CrudRepository<User, Long>, QueryByExampleExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      User find(Example<User> example) {
                          return users.findOne(example).orElse(null);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void byPredicate() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13")
                .dependsOn(QUERYDSL_PREDICATE, QUERYDSL_ORDER_SPECIFIER)),
              USER,
              java(
                """
                  import com.acme.User;
                  import com.querydsl.core.types.Predicate;
                  import org.springframework.data.querydsl.QueryDslPredicateExecutor;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long>, QueryDslPredicateExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      User find(Predicate predicate) {
                          return users.findOne(predicate);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import com.querydsl.core.types.Predicate;
                  import org.springframework.data.querydsl.QueryDslPredicateExecutor;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long>, QueryDslPredicateExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      User find(Predicate predicate) {
                          return users.findOne(predicate).orElse(null);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void redeclaredByARepository() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13")
                .dependsOn(SPECIFICATION, JPA_SPECIFICATION_EXECUTOR)),
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.jpa.domain.Specification;
                  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                  import org.springframework.data.repository.Repository;

                  interface UserRepository extends Repository<User, Long>, JpaSpecificationExecutor<User> {
                      @Override
                      User findOne(Specification<User> spec);
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.jpa.domain.Specification;
                  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                  import org.springframework.data.repository.Repository;

                  import java.util.Optional;

                  interface UserRepository extends Repository<User, Long>, JpaSpecificationExecutor<User> {
                      @Override
                      Optional<User> findOne(Specification<User> spec);
                  }
                  """
              )
            );
        }

        @Test
        void idOverloadOnARepositoryWithBoth() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13")
                .dependsOn(SPECIFICATION, JPA_SPECIFICATION_EXECUTOR)),
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.jpa.domain.Specification;
                  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long>, JpaSpecificationExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id, Specification<User> spec) {
                          User found = users.findOne(id);
                          return found == null ? users.findOne(spec) : found;
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.jpa.domain.Specification;
                  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long>, JpaSpecificationExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id, Specification<User> spec) {
                          User found = users.findById(id).orElse(null);
                          return found == null ? users.findOne(spec).orElse(null) : found;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void redeclaredIdOverloadOnARepositoryWithBoth() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13")
                .dependsOn(SPECIFICATION, JPA_SPECIFICATION_EXECUTOR)),
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long>, JpaSpecificationExecutor<User> {
                      @Override
                      User findOne(Long id);
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id) {
                          return users.findOne(id);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                  import org.springframework.data.repository.CrudRepository;

                  import java.util.Optional;

                  interface UserRepository extends CrudRepository<User, Long>, JpaSpecificationExecutor<User> {
                      @Override
                      Optional<User> findById(Long id);
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id) {
                          return users.findById(id).orElse(null);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void overrideWithABodyIsLeftWithItsCallers() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13")
                .dependsOn(SPECIFICATION, JPA_SPECIFICATION_EXECUTOR)),
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.jpa.domain.Specification;
                  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

                  abstract class Users implements JpaSpecificationExecutor<User> {
                      @Override
                      public User findOne(Specification<User> spec) {
                          return null;
                      }

                      User first(Specification<User> spec) {
                          return this.findOne(spec);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void methodReferenceIsLeftForTheCompilerToPointOut() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.domain.Example;
                  import org.springframework.data.repository.CrudRepository;
                  import org.springframework.data.repository.query.QueryByExampleExecutor;
                  import java.util.function.Function;

                  interface UserRepository extends CrudRepository<User, Long>, QueryByExampleExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;
                      Function<Example<User>, User> finder = users::findOne;
                  }
                  """
              )
            );
        }

        @Test
        void alreadyOnSpringData2() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                "spring-data-commons-2.7")),
              java(
                """
                  import org.springframework.data.domain.Example;
                  import org.springframework.data.repository.CrudRepository;
                  import org.springframework.data.repository.query.QueryByExampleExecutor;
                  import java.util.Optional;

                  class User {
                  }

                  interface UserRepository extends CrudRepository<User, Long>, QueryByExampleExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      Optional<User> find(Example<User> example) {
                          return users.findOne(example);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class NotTheMethodsThatWereRenamed {

        @Test
        void overloadDeclaredByTheRepository() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.repository.CrudRepository;

                  interface UserRepository extends CrudRepository<User, Long> {
                      User findOne(String name);
                      boolean exists(String name);
                      void delete(String name);
                  }

                  class UserService {
                      UserRepository users;

                      User find(String name) {
                          users.delete(name);
                          return users.exists(name) ? users.findOne(name) : null;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void existsByExampleKeepsItsName() {
            rewriteRun(
              USER,
              java(
                """
                  import com.acme.User;
                  import org.springframework.data.domain.Example;
                  import org.springframework.data.repository.CrudRepository;
                  import org.springframework.data.repository.query.QueryByExampleExecutor;

                  interface UserRepository extends CrudRepository<User, Long>, QueryByExampleExecutor<User> {
                  }

                  class UserService {
                      UserRepository users;

                      boolean has(Example<User> example) {
                          return users.exists(example);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void unrelatedTypeWithTheSameMethodNames() {
            rewriteRun(
              java(
                """
                  class Store {
                      Object findOne(Long id) {
                          return null;
                      }

                      void delete(Long id) {
                      }
                  }

                  class Service {
                      Store store;

                      Object find(Long id) {
                          store.delete(id);
                          return store.findOne(id);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void alreadyOnSpringData2() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                "spring-data-commons-2.7")),
              java(
                """
                  import org.springframework.data.repository.CrudRepository;
                  import java.util.List;

                  class User {
                  }

                  interface UserRepository extends CrudRepository<User, Long> {
                      User findOne(Long id);
                      boolean exists(Long id);
                      void delete(Long id);
                      List<User> findAll(Iterable<Long> ids);
                  }

                  class UserService {
                      UserRepository users;

                      User find(Long id, List<Long> ids) {
                          users.delete(id);
                          users.findAll(ids);
                          return users.exists(id) ? users.findOne(id) : null;
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Mockito {

        @Test
        void stubbingReturnsAnOptional() {
            rewriteRun(
              USER,
              USER_REPOSITORY,
              java(
                """
                  import com.acme.User;
                  import com.acme.UserRepository;

                  import static org.mockito.ArgumentMatchers.any;
                  import static org.mockito.Mockito.mock;
                  import static org.mockito.Mockito.when;

                  class UserServiceTest {
                      UserRepository users = mock(UserRepository.class);

                      void found(User user) {
                          when(users.findOne(1L)).thenReturn(user);
                      }

                      void notFound() {
                          when(users.findOne(any(Long.class))).thenReturn(null);
                      }

                      void multiline(User user) {
                          when(users.findOne(1L))
                              .thenReturn(user);
                      }

                      void consecutive(User first, User second) {
                          when(users.findOne(1L)).thenReturn(first, second).thenReturn(null);
                      }

                      void failure() {
                          when(users.findOne(1L)).thenThrow(new IllegalStateException());
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import com.acme.UserRepository;

                  import java.util.Optional;

                  import static org.mockito.ArgumentMatchers.any;
                  import static org.mockito.Mockito.mock;
                  import static org.mockito.Mockito.when;

                  class UserServiceTest {
                      UserRepository users = mock(UserRepository.class);

                      void found(User user) {
                          when(users.findById(1L)).thenReturn(Optional.ofNullable(user));
                      }

                      void notFound() {
                          when(users.findById(any(Long.class))).thenReturn(Optional.empty());
                      }

                      void multiline(User user) {
                          when(users.findById(1L))
                              .thenReturn(Optional.ofNullable(user));
                      }

                      void consecutive(User first, User second) {
                          when(users.findById(1L)).thenReturn(Optional.ofNullable(first), Optional.ofNullable(second)).thenReturn(Optional.empty());
                      }

                      void failure() {
                          when(users.findById(1L)).thenThrow(new IllegalStateException());
                      }
                  }
                  """
              )
            );
        }

        @Test
        void bddStubbing() {
            rewriteRun(
              USER,
              USER_REPOSITORY,
              java(
                """
                  import com.acme.User;
                  import com.acme.UserRepository;
                  import org.mockito.BDDMockito;

                  import static org.mockito.BDDMockito.given;
                  import static org.mockito.BDDMockito.then;
                  import static org.mockito.BDDMockito.willReturn;

                  class UserServiceTest {
                      UserRepository users;

                      void stub(User user) {
                          given(users.findOne(1L)).willReturn(user);
                          BDDMockito.given(users.findOne(2L)).willReturn(null);
                          willReturn(user).given(users).findOne(3L);
                      }

                      void check() {
                          then(users).should().findOne(1L);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import com.acme.UserRepository;
                  import org.mockito.BDDMockito;

                  import java.util.Optional;

                  import static org.mockito.BDDMockito.given;
                  import static org.mockito.BDDMockito.then;
                  import static org.mockito.BDDMockito.willReturn;

                  class UserServiceTest {
                      UserRepository users;

                      void stub(User user) {
                          given(users.findById(1L)).willReturn(Optional.ofNullable(user));
                          BDDMockito.given(users.findById(2L)).willReturn(Optional.empty());
                          willReturn(Optional.ofNullable(user)).given(users).findById(3L);
                      }

                      void check() {
                          then(users).should().findById(1L);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doReturnWhen() {
            rewriteRun(
              USER,
              USER_REPOSITORY,
              java(
                """
                  import com.acme.User;
                  import com.acme.UserRepository;

                  import static org.mockito.Mockito.doReturn;
                  import static org.mockito.Mockito.doThrow;

                  class UserServiceTest {
                      UserRepository users;

                      void stub(User user) {
                          doReturn(user).when(users).findOne(1L);
                          doReturn(null).when(users).findOne(2L);
                          doThrow(new IllegalStateException()).when(users).findOne(3L);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import com.acme.UserRepository;

                  import java.util.Optional;

                  import static org.mockito.Mockito.doReturn;
                  import static org.mockito.Mockito.doThrow;

                  class UserServiceTest {
                      UserRepository users;

                      void stub(User user) {
                          doReturn(Optional.ofNullable(user)).when(users).findById(1L);
                          doReturn(Optional.empty()).when(users).findById(2L);
                          doThrow(new IllegalStateException()).when(users).findById(3L);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void verification() {
            rewriteRun(
              USER,
              USER_REPOSITORY,
              java(
                """
                  import com.acme.UserRepository;
                  import org.mockito.InOrder;

                  import static org.mockito.Mockito.never;
                  import static org.mockito.Mockito.times;
                  import static org.mockito.Mockito.verify;

                  class UserServiceTest {
                      UserRepository users;

                      void check(InOrder inOrder) {
                          verify(users).findOne(1L);
                          verify(users, times(2)).findOne(1L);
                          verify(users, never()).exists(1L);
                          verify(users).delete(1L);
                          inOrder.verify(users).findOne(1L);
                      }
                  }
                  """,
                """
                  import com.acme.UserRepository;
                  import org.mockito.InOrder;

                  import static org.mockito.Mockito.never;
                  import static org.mockito.Mockito.times;
                  import static org.mockito.Mockito.verify;

                  class UserServiceTest {
                      UserRepository users;

                      void check(InOrder inOrder) {
                          verify(users).findById(1L);
                          verify(users, times(2)).findById(1L);
                          verify(users, never()).existsById(1L);
                          verify(users).deleteById(1L);
                          inOrder.verify(users).findById(1L);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void onlyFindOneChangesItsReturnType() {
            rewriteRun(
              USER,
              USER_REPOSITORY,
              java(
                """
                  import com.acme.User;
                  import com.acme.UserRepository;
                  import java.util.List;

                  import static org.mockito.Mockito.when;

                  class UserServiceTest {
                      UserRepository users;

                      void stub(User user, List<User> all) {
                          when(users.exists(1L)).thenReturn(true);
                          when(users.save(all)).thenReturn(all);
                          when(users.save(user)).thenReturn(user);
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import com.acme.UserRepository;
                  import java.util.List;

                  import static org.mockito.Mockito.when;

                  class UserServiceTest {
                      UserRepository users;

                      void stub(User user, List<User> all) {
                          when(users.existsById(1L)).thenReturn(true);
                          when(users.saveAll(all)).thenReturn(all);
                          when(users.save(user)).thenReturn(user);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void findOneAsAnArgumentToAStubbedCallIsStillAValue() {
            rewriteRun(
              USER,
              USER_REPOSITORY,
              java(
                """
                  import com.acme.User;
                  import com.acme.UserRepository;

                  import static org.mockito.Mockito.when;

                  class UserServiceTest {
                      UserRepository users;
                      java.util.function.Function<User, String> names;

                      void stub() {
                          when(names.apply(users.findOne(1L))).thenReturn("bob");
                      }
                  }
                  """,
                """
                  import com.acme.User;
                  import com.acme.UserRepository;

                  import static org.mockito.Mockito.when;

                  class UserServiceTest {
                      UserRepository users;
                      java.util.function.Function<User, String> names;

                      void stub() {
                          when(names.apply(users.findById(1L).orElse(null))).thenReturn("bob");
                      }
                  }
                  """
              )
            );
        }
    }
}

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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-spring/issues/613")
class MigrateAuditorAwareToOptionalTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13"))
          .recipe(new MigrateAuditorAwareToOptional());
    }

    @DocumentExample
    @Test
    void rewriteImplementation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              public class MyAuditorAware implements AuditorAware<String> {
                  @Override
                  public String getCurrentAuditor() {
                      return "admin";
                  }
              }
              """, """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class MyAuditorAware implements AuditorAware<String> {
                  @Override
                  public Optional<java.lang.String> getCurrentAuditor() {
                      return Optional.ofNullable("admin");
                  }
              }
              """
          )
        );
    }

    @Test
    void rewriteLambdaLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return () -> "admin";
                  }
              }
              """, """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return () -> Optional.ofNullable("admin");
                  }
              }
              """
          )
        );
    }

    @Test
    void rewriteLambdaBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Objects;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return () -> {
                          return Objects.toString("admin");
                      };
                  }
              }
              """, """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Objects;
              import java.util.Optional;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return () -> {
                          return Optional.ofNullable(Objects.toString("admin"));
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void rewriteInterfaceInstantiation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return new AuditorAware<String>() {
                          @Override
                          public String getCurrentAuditor() {
                              return "admin";
                          }
                      };
                  }
              }
              """, """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return new AuditorAware<String>() {
                          @Override
                          public Optional<java.lang.String> getCurrentAuditor() {
                              return Optional.ofNullable("admin");
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void rewriteMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return this::getCurrentAuditor;
                  }

                  public String getCurrentAuditor() {
                      return "admin";
                  }
              }
              """, """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return () -> Optional.ofNullable(this.getCurrentAuditor());
                  }

                  public String getCurrentAuditor() {
                      return "admin";
                  }
              }
              """
          )
        );
    }

    @Test
    void dontRewriteImplementation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class MyAuditorAware implements AuditorAware<String> {
                  @Override
                  public Optional<String> getCurrentAuditor() {
                      return Optional.ofNullable("admin");
                  }
              }
              """
          )
        );
    }

    @Test
    void dontRewriteLambdaLiteral() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-2")),
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return () -> Optional.ofNullable("admin");
                  }
              }
              """
          )
        );
    }

    @Test
    void dontRewriteLambdaBlock() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-2")),
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Objects;
              import java.util.Optional;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return () -> {
                          return Optional.ofNullable(Objects.toString("admin"));
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void dontRewriteInterfaceInstantiation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return new AuditorAware<String>() {
                          @Override
                          public Optional<String> getCurrentAuditor() {
                              return Optional.ofNullable("admin");
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void complexerObjects() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              public class Configuration {

                  public AuditorAware<User> auditorAware() {
                      return this::determineUser;
                  }

                  public User determineUser() {
                      return new User("admin");
                  }

                  public static class User {
                      private final String name;

                      public User(String name) {
                          this.name = name;
                      }
                  }
              }
              """, """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {

                  public AuditorAware<User> auditorAware() {
                      return () -> Optional.ofNullable(this.determineUser());
                  }

                  public User determineUser() {
                      return new User("admin");
                  }

                  public static class User {
                      private final String name;

                      public User(String name) {
                          this.name = name;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dontRewriteOptionalObjectMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {

                  public AuditorAware<User> auditorAware() {
                      return this::determineUser;
                  }

                  public Optional<User> determineUser() {
                      return Optional.of(new User("admin"));
                  }

                  public static class User {
                      private final String name;

                      public User(String name) {
                          this.name = name;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dontRewriteOptionalMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return this::getCurrentAuditor;
                  }

                  public Optional<String> getCurrentAuditor() {
                      return Optional.ofNullable("admin");
                  }
              }
              """
          )
        );
    }

    @Test
    void complexerObjectsCalls() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              public class Configuration {

                  public AuditorAware<String> auditorAware() {
                      return () -> {
                          User u = this.determineUser();
                          return u.getName();
                      };
                  }

                  public User determineUser() {
                      return new User("admin");
                  }

                  public static class User {
                      private final String name;

                      public User(String name) {
                          this.name = name;
                      }

                      public String getName() {
                          return name;
                      }
                  }
              }
              """, """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {

                  public AuditorAware<String> auditorAware() {
                      return () -> {
                          User u = this.determineUser();
                          return Optional.ofNullable(u.getName());
                      };
                  }

                  public User determineUser() {
                      return new User("admin");
                  }

                  public static class User {
                      private final String name;

                      public User(String name) {
                          this.name = name;
                      }

                      public String getName() {
                          return name;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dontRewriteOptionalObjectMethodInvocations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.AuditorAware;

              import java.util.Optional;

              public class Configuration {

                  public AuditorAware<String> auditorAware() {
                      return () -> {
                          User u = this.determineUser();
                          return u.getName();
                      };
                  }

                  public User determineUser() {
                      return new User("admin");
                  }

                  public static class User {
                      private final String name;

                      public User(String name) {
                          this.name = name;
                      }

                      public Optional<String> getName() {
                          return Optional.ofNullable(name);
                      }
                  }
              }
              """
          )
        );
    }
}

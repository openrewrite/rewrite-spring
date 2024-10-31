/*
 * Copyright 2024 the original author or authors.
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
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-1.13", "spring-data-commons-2.7"))
          .recipe(new MigrateAuditorAwareToOptional());
    }

    @DocumentExample
    @Test
    void rewriteImplementation() {
        rewriteRun(
          //language=java
          java(
            """
              package sample;
              
              import org.springframework.data.domain.AuditorAware;
              
              public class MyAuditorAware implements AuditorAware<String> {
                  @Override
                  public String getCurrentAuditor() {
                      return "admin";
                  }
              }
              """, """
              package sample;
              
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
        //TODO Question for TIM:
        //sample/Configuration.java:7: error: incompatible types: bad return type in lambda expression
        //       return () -> "admin";
        //                    ^
        //   String cannot be converted to Optional<String>
        //
        //LST contains missing or invalid type information
        //MethodInvocation->Lambda->Return->Block->MethodDeclaration->Block->ClassDeclaration->CompilationUnit
        // *~~(MethodInvocation type is missing or malformed)~~>*/Optional.ofNullable("admin")
        rewriteRun(
          //language=java
          java(
            """
              package sample;
              
              import org.springframework.data.domain.AuditorAware;
              
              public class Configuration {
                  public AuditorAware<String> auditorAware() {
                      return () -> "admin";
                  }
              }
              """, """
              package sample;
              
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
              package sample;
              
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
              package sample;
              
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
        //TODO Question for TIM: how to get rid of the types? I have the imports.
        //- public Optional<String> getCurrentAuditor() {
        //+ public java.util.Optional<java.lang.String> getCurrentAuditor() {
        rewriteRun(
          //language=java
          java(
            """
              package sample;
              
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
              package sample;
              
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
              package sample;
              
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
              package sample;
              
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
              package sample;
              
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
          //language=java
          java(
            """
              package sample;
              
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
          //language=java
          java(
            """
              package sample;
              
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
              package sample;
              
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
    void dontRewriteOptionalMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              package sample;
              
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
}

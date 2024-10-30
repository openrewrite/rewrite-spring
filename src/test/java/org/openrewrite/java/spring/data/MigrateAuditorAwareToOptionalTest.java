package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-spring/issues/613")
class MigrateAuditorAwareToOptionalTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new MigrateAuditorAwareToOptional());
    }

    @Test
    void rewriteImplementation() {
        rewriteRun(
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
                  public Optional<String> getCurrentAuditor() {
                      return Optional.ofNullable("admin");
                  }
              }
              """
          )
        );
    }

    @Test
    void rewriteFunctionalInterface() {
        rewriteRun(
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
    void rewriteFunctionalImplementation() {
        rewriteRun(
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
    void rewriteMethodReference() {
        rewriteRun(
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
                      return () -> Optional.ofNullable(getCurrentAuditor());
                  }
              
                  public String getCurrentAuditor() {
                      return "admin";
                  }
              }
              """
          )
        );
    }
}
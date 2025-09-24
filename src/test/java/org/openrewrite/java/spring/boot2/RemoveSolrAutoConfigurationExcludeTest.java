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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot3.RemoveSolrAutoConfigurationExclude;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveSolrAutoConfigurationExcludeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveSolrAutoConfigurationExclude())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot"));
    }

    @DocumentExample
    @Test
    void removeFromArray() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @SpringBootApplication(exclude = { SecurityAutoConfiguration.class, SolrAutoConfiguration.class })
              public class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

              @SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
              public class Application {
              }
              """
          )
        );
    }

    @Test
    void removeEntireArray() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @SpringBootApplication(exclude = { SolrAutoConfiguration.class })
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              class Application {
              }
              """
          )
        );
    }

    @Test
    void removeArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @SpringBootApplication(exclude = SolrAutoConfiguration.class)
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              class Application {
              }
              """
          )
        );
    }

    @Test
    void removeFullyQualifiedArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication(exclude = org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration.class)
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              class Application {
              }
              """
          )
        );
    }

    @Test
    void removeFromEnableAutoConfiguration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @EnableAutoConfiguration(exclude = SolrAutoConfiguration.class)
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

              @EnableAutoConfiguration
              class Application {
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void retainOtherAutoConfigurationClasses() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.boot.autoconfigure.SpringBootApplication;
                  import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

                  @SpringBootApplication(exclude = SecurityAutoConfiguration.class)
                  public class Application {
                  }
                  """
              )
            );
        }

        @Test
        void retainOtherAutoConfigurationClassesInArray() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.boot.autoconfigure.SpringBootApplication;
                  import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

                  @SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
                  public class Application {
                  }
                  """
              )
            );
        }
    }
}

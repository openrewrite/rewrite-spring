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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConvertAutoConfigurationExcludeToExcludeNameTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertAutoConfigurationExcludeToExcludeName(
                        "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration"))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-2.2",
            "spring-security-core-5.1",
            "spring-security-web-5.1",
            "jakarta.servlet-api-6",
            "spring-boot-autoconfigure-2.3"));
    }

    @DocumentExample
    @Test
    void convertSingleExcludeToExcludeName() {
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

              @SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration")
              class Application {
              }
              """
          )
        );
    }

    @Test
    void convertFromExcludeArrayLeavingOthers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @SpringBootApplication(exclude = { SecurityAutoConfiguration.class, SolrAutoConfiguration.class })
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

              @SpringBootApplication(exclude = { SecurityAutoConfiguration.class }, excludeName = "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration")
              class Application {
              }
              """
          )
        );
    }

    @Test
    void convertFullyQualifiedExclude() {
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

              @SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration")
              class Application {
              }
              """
          )
        );
    }

    @Test
    void convertFromEnableAutoConfiguration() {
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

              @EnableAutoConfiguration(excludeName = "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration")
              class Application {
              }
              """
          )
        );
    }

    @Test
    void mergeIntoExistingExcludeNameScalar() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @SpringBootApplication(
                  exclude = SolrAutoConfiguration.class,
                  excludeName = "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
              )
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication(
                  excludeName = { "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration", "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration" }
              )
              class Application {
              }
              """
          )
        );
    }

    @Test
    void mergeIntoExistingExcludeNameArray() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @SpringBootApplication(
                  exclude = SolrAutoConfiguration.class,
                  excludeName = { "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration" }
              )
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication(
                  excludeName = { "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration", "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration" }
              )
              class Application {
              }
              """
          )
        );
    }

    @Test
    void doNotDuplicateIfAlreadyInExcludeName() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @SpringBootApplication(
                  exclude = SolrAutoConfiguration.class,
                  excludeName = "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration"
              )
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication(
                  excludeName = "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration"
              )
              class Application {
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void noExcludeAttribute() {
            rewriteRun(
              //language=java
              java(
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
        void otherClassInExclude() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.boot.autoconfigure.SpringBootApplication;
                  import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

                  @SpringBootApplication(exclude = SecurityAutoConfiguration.class)
                  class Application {
                  }
                  """
              )
            );
        }

        @Test
        void alreadyInExcludeNameOnly() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration")
                  class Application {
                  }
                  """
              )
            );
        }
    }
}

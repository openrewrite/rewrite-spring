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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot3.RemoveSolrAutoConfigurationExclude;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveSolrAutoConfigurationExcludeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveSolrAutoConfigurationExclude())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot"));
    }

    @Test
    @DocumentExample
        // Padding doesn't stay (array initializer loses empty space at the end) intact so test fails
    void removeSolrAutoConfigurationExcludeList() {
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

              @SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
              public class Application {
              }
              """
          )
        );
    }

    @Test
    void removeSolrAutoConfigurationExcludeListSingleElement() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @SpringBootApplication(exclude = { SolrAutoConfiguration.class })
              public class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class Application {
              }
              """
          )
        );
    }

    @Test
    void removeSolrAutoConfigurationExclude() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;

              @SpringBootApplication(exclude = SolrAutoConfiguration.class)
              public class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class Application {
              }
              """
          )
        );
    }

    @Test
    void shouldRemain() {
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
    void shouldRemainList() {
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

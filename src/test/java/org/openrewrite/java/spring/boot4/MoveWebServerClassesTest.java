/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MoveWebServerClassesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource(
          "/META-INF/rewrite/spring-boot-40-web-server.yml",
          "org.openrewrite.java.spring.boot4.MoveWebServerClasses"
        ).parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "spring-boot-3"));
    }

    @DocumentExample
    @Test
    void movesEmbeddedTomcatClasses() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;

              class A {
                  Class<?> type = TomcatWebServer.class;
              }
              """,
            """
              import org.springframework.boot.tomcat.TomcatWebServer;

              class A {
                  Class<?> type = TomcatWebServer.class;
              }
              """
          )
        );
    }

    @Test
    void movesEmbeddedJettyClasses() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.web.embedded.jetty.JettyWebServer;

              class A {
                  Class<?> type = JettyWebServer.class;
              }
              """,
            """
              import org.springframework.boot.jetty.JettyWebServer;

              class A {
                  Class<?> type = JettyWebServer.class;
              }
              """
          )
        );
    }

    @Test
    void movesServletWebServerApplicationContext() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

              class A {
                  Class<?> type = ServletWebServerApplicationContext.class;
              }
              """,
            """
              import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

              class A {
                  Class<?> type = ServletWebServerApplicationContext.class;
              }
              """
          )
        );
    }

    @Test
    void movesReactiveWebServerApplicationContext() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;

              class A {
                  Class<?> type = ReactiveWebServerApplicationContext.class;
              }
              """,
            """
              import org.springframework.boot.web.server.reactive.context.ReactiveWebServerApplicationContext;

              class A {
                  Class<?> type = ReactiveWebServerApplicationContext.class;
              }
              """
          )
        );
    }
}

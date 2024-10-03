package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateResponseStatusExceptionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.MigrateResponseStatusException")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),  "spring-core-5.3", "spring-beans-5.3", "spring-web-5.3"));
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-spring/issues/554")
    @Test
    void migrateResponseStatusExceptionGetStatusMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpStatus;
              import org.springframework.web.server.ResponseStatusException;
              class A {
                  void foo(ResponseStatusException e) {
                      HttpStatus i = e.getStatus();
                  }
              }
              """,
            """
              import org.springframework.http.HttpStatus;
              import org.springframework.web.server.ResponseStatusException;
              class A {
                  void foo(ResponseStatusException e) {
                      HttpStatus i = e.getStatusCode();
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-spring/issues/554")
    @Test
    void migrateResponseStatusExceptionGetRawStatusCodeMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.server.ResponseStatusException;
              class A {
                  void foo(ResponseStatusException e) {
                      int i = e.getRawStatusCode();
                  }
              }
              """,
            """
              import org.springframework.web.server.ResponseStatusException;
              class A {
                  void foo(ResponseStatusException e) {
                      int i = e.getStatusCode().value();
                  }
              }
              """
          )
        );
    }

}

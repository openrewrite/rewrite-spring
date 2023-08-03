package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.http.SimplifyMediaTypeParseCalls;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class SimplifyMediaTypeParseCallsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new SimplifyMediaTypeParseCalls())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web"));
    }

    @DocumentExample
    @Test
    void replaceUnnecessaryParseCall() {
        //language=java
        rewriteRun(
          java(
            """
              package com.mycompany;
              
              import org.springframework.http.MediaType;
              
              class Test {
                  void test() {
                      MediaType.parse("application/json");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.http.MediaType;
              
              class Test {
                  void test() {
                      MediaType.APPLICATION_JSON;
                  }
              }
              """
          )
        );
    }
}

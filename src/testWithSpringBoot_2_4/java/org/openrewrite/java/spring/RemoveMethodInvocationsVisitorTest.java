package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.AdHocRecipe;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.java.Assertions.java;

public class RemoveMethodInvocationsVisitorTest implements RewriteTest {

    private Recipe createRemoveMethodsRecipe(String... methods) {
        return new AdHocRecipe(null, null, null, () -> new RemoveMethodInvocationsVisitor(List.of(methods)), null, null, null, emptyList());
    }

    @Test
    void removeFromEnd() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(createRemoveMethodsRecipe("java.lang.StringBuilder toString()"))
          ,
          java(
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("Hello")
                          .append(" ")
                          .append("World")
                          .reverse()
                          .append(" ")
                          .reverse()
                          .append("Yeah")
                          .toString();
                  }
              }
              """,
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("Hello")
                          .append(" ")
                          .append("World")
                          .reverse()
                          .append(" ")
                          .reverse()
                          .append("Yeah");
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMultipleMethodsFromEnd() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(createRemoveMethodsRecipe("java.lang.StringBuilder toString()", "java.lang.StringBuilder append(java.lang.String)")),
          java(
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("Hello")
                          .append(" ")
                          .append("World")
                          .reverse()
                          .append(" ")
                          .reverse()
                          .append("Yeah")
                          .toString();
                  }
              }
              """,
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                      sb.reverse()
                          .reverse();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFromMiddle() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(createRemoveMethodsRecipe("java.lang.StringBuilder reverse()")),
          java(
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("Hello")
                          .append(" ")
                          .append("World")
                          .reverse()
                          .append(" ")
                          .reverse()
                          .append("Yeah")
                          .toString();
                  }
              }
              """,
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("Hello")
                          .append(" ")
                          .append("World")
                          .append(" ")
                          .append("Yeah")
                          .toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeEntireStatement() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(createRemoveMethodsRecipe("java.lang.StringBuilder append(java.lang.String)")),
          java(
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("Hello");
                  }
              }
              """,
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepSelectForAssignment() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(createRemoveMethodsRecipe("java.lang.StringBuilder append(java.lang.String)")),
          java(
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                      StringBuilder sb2 = sb.append("foo");
                      sb2.append("bar");
                      sb2.reverse();
                  }
              }
              """,
            """
              public class Test {
                  void method() {
                      StringBuilder sb = new StringBuilder();
                      StringBuilder sb2 = sb;
                      sb2.reverse();
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedCallsAsParameter() {
        rewriteRun(
          spec -> spec.recipe(createRemoveMethodsRecipe("java.lang.StringBuilder append(java.lang.String)")),
          java(
            """
              class Test {
                  void method() {
                      print(new StringBuilder()
                          .append("Hello")
                          .append(" ")
                          .append("World")
                          .reverse()
                          .append(" ")
                          .reverse()
                          .append("Yeah")
                          .toString());
                  }
                  void print(String str) {}
              }
              """,
            """
              class Test {
                  void method() {
                      print(new StringBuilder()
                          .reverse()
                          .reverse()
                          .toString());
                  }
                  void print(String str) {}
              }
              """
          )
        );
    }
}

package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

public class RemoveMethodInvocationsTest implements RewriteTest {

    @Test
    void removeFromEnd() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new RemoveMethodInvocations(List.of("java.lang.StringBuilder toString()"))),
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
          spec -> spec.recipe(new RemoveMethodInvocations(List.of("java.lang.StringBuilder toString()", "java.lang.StringBuilder append(java.lang.String)"))),
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
          spec -> spec.recipe(new RemoveMethodInvocations(List.of("java.lang.StringBuilder reverse()"))),
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
          spec -> spec.recipe(new RemoveMethodInvocations(List.of("java.lang.StringBuilder append(java.lang.String)"))),
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
    void doNotChangeAssignment() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new RemoveMethodInvocations(List.of("java.lang.StringBuilder append(java.lang.String)"))),
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
                      StringBuilder sb2 = sb.append("foo");
                      sb2.reverse();
                  }
              }
              """
          )
        );
    }

}

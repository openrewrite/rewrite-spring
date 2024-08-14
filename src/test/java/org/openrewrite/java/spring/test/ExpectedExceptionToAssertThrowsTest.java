package org.openrewrite.java.spring.test;

import static org.openrewrite.java.Assertions.java;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class ExpectedExceptionToAssertThrowsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExpectedExceptionToAssertThrows())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-boot-test", "spring-test")
          );
    }

    @Test
    @DocumentExample
    void thatExpectedExceptionIsMigratedToAssertThrows() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              class SomeTest {

                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void test() {
                      thrown.expectMessage("exception");
                      throw new RuntimeException("exception");
                  }
              } 
              """,
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.ExpectedException;

              class SomeTest {

                  @Rule
                  public ExpectedException thrown = ExpectedException.none();

                  @Test
                  public void test() {
                      thrown.expectMessage("exception");
                      throw new RuntimeException("exception");
                  }
              }    
              """
          )
        );
    }
}

package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceDeprecatedStringUtilsIsEmptyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec){
        spec.recipe(new StringOptimizationRecipes()).parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),"spring-core-5.+"));
    }

    @DocumentExample
    @Test
    @SuppressWarnings("deprecation")
    void replaceStringUtilsIsEmpty() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.util.StringUtils;
              class Test {
                    void test(String s) {
                      return StringUtils.isEmpty(s);
                    }
              }
              """,
            """
              import org.springframework.util.StringUtils;
              class Test {
                    void test(String s) {
                      return !StringUtils.hasLength(s);
                    }
              }
              """
          )
        );
    }
}

package org.openrewrite.java.spring.batch;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class UpgradeSkipPolicyParameterTypeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResource("/META-INF/rewrite/spring-batch-5.0.yml", "org.openrewrite.java.spring.batch.UpgradeSkipPolicyParameterType")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4.3.+", "spring-batch-infrastructure-4.3.+", "spring-beans-4.3.30.RELEASE", "spring-batch", "spring-boot", "spring-core", "spring-context"));
    }

    @DocumentExample
    @Test
    void testReplaceParameter() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.core.step.skip.SkipLimitExceededException;
              import org.springframework.batch.core.step.skip.SkipPolicy;

              public class MySkipPolicy implements SkipPolicy {
                  @Override
                  public boolean shouldSkip(Throwable throwable, int skipCount) throws SkipLimitExceededException {
                      return true;
                  }
              }
              """,
            """
              import org.springframework.batch.core.step.skip.SkipLimitExceededException;
              import org.springframework.batch.core.step.skip.SkipPolicy;

              public class MySkipPolicy implements SkipPolicy {
                  @Override
                  public boolean shouldSkip(Throwable throwable, long skipCount) throws SkipLimitExceededException {
                      return true;
                  }
              }
              """
          )
        );
    }
}

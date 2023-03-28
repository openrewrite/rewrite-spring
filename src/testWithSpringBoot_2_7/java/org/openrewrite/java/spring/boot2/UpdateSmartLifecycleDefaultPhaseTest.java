package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot3.UpdateSmartLifecycleDefaultPhase;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class UpdateSmartLifecycleDefaultPhaseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("spring-context"))
          .recipe(new UpdateSmartLifecycleDefaultPhase());
    }

    @Test
    void replace() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.SmartLifecycle;

              class MyLifeCycle implements SmartLifecycle  {
              	@Override
              	public void start() {}

              	@Override
              	public void stop() {}

              	@Override
              	public boolean isRunning() { return false; }

              	@Override
              	public int getPhase() {
              		return Integer.MAX_VALUE - 1;
              	}
              }
              """,
            """
              import org.springframework.context.SmartLifecycle;

              class MyLifeCycle implements SmartLifecycle  {
              	@Override
              	public void start() {}

              	@Override
              	public void stop() {}

              	@Override
              	public boolean isRunning() { return false; }

              	@Override
              	public int getPhase() {
              		return DEFAULT_PHASE - 1;
              	}
              }
              """
          )
        );
    }
}

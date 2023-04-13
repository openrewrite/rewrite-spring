package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security5.ReplaceGlobalMethodSecurityWithMethodSecurity;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class ReplaceGlobalMethodSecurityWithMethodSecurityTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceGlobalMethodSecurityWithMethodSecurity())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),"spring-security-config-5.8.+"));
    }

    @Test
    void replaceWithPrePostEnabled() {
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

              @EnableGlobalMethodSecurity(prePostEnabled = true)
              public class config {
              }
              """,
            """
              import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

              @EnableMethodSecurity
              public class config {
              }
              """
          )
        );
    }

    @Test
    void replaceWithNotPrePostEnabled() {
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

              @EnableGlobalMethodSecurity(securedEnabled = true)
              public class config {
              }
              """,
            """
              import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

              @EnableMethodSecurity(securedEnabled = true, prePostEnabled = false)
              public class config {
              }
              """
          )
        );
    }
}

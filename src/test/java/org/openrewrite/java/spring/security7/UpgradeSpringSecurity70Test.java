package org.openrewrite.java.spring.security7;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeSpringSecurity70Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources(
            "org.openrewrite.java.spring.security7.UpgradeSpringSecurity_7_0")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-security-core-6"));
    }

    @DocumentExample
    @Test
    void migrateAuthorizationManagerCheckToAuthorize() {
        rewriteRun(
          java(
            """
              import org.springframework.security.authorization.AuthorizationManager;
              import org.springframework.security.authorization.AuthorizationDecision;

              class MyAuthz {
                  void test(AuthorizationManager<Object> manager) {
                      AuthorizationDecision decision = manager.check(null, null);
                  }
              }
              """,
            """
              import org.springframework.security.authorization.AuthorizationManager;
              import org.springframework.security.authorization.AuthorizationDecision;

              class MyAuthz {
                  void test(AuthorizationManager<Object> manager) {
                      AuthorizationDecision decision = manager.authorize(null, null);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenCheckNotUsed() {
        rewriteRun(
          java(
            """
              import org.springframework.security.authorization.AuthorizationManager;

              class MyAuthz {
                  AuthorizationManager<Object> manager;
              }
              """
          )
        );
    }
}

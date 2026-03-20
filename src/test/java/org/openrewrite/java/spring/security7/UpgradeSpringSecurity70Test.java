/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

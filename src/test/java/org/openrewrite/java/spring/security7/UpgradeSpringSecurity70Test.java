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

    @Test
    void widenSupplierParameterOfRenamedOverride() {
        rewriteRun(
          java(
            """
              import java.util.function.Supplier;
              import org.springframework.security.authorization.AuthorizationDecision;
              import org.springframework.security.authorization.AuthorizationManager;
              import org.springframework.security.core.Authentication;

              class CustomAuthorizationManager implements AuthorizationManager<Object> {
                  @Override
                  public AuthorizationDecision check(Supplier<Authentication> authentication, Object object) {
                      Authentication current = authentication.get();
                      return new AuthorizationDecision(current.isAuthenticated());
                  }
              }
              """,
            """
              import java.util.function.Supplier;
              import org.springframework.security.authorization.AuthorizationDecision;
              import org.springframework.security.authorization.AuthorizationManager;
              import org.springframework.security.core.Authentication;

              class CustomAuthorizationManager implements AuthorizationManager<Object> {
                  @Override
                  public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication, Object object) {
                      Authentication current = authentication.get();
                      return new AuthorizationDecision(current.isAuthenticated());
                  }
              }
              """
          )
        );
    }

    @Test
    void inlinePasswordGrantParameterNames() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
            //language=java
            """
              package org.springframework.security.oauth2.core.endpoint;
              public final class OAuth2ParameterNames {
                  public static final String GRANT_TYPE = "grant_type";
                  public static final String USERNAME = "username";
                  public static final String PASSWORD = "password";
              }
              """
          )),
          java(
            """
              import java.util.Map;

              import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.GRANT_TYPE;
              import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.PASSWORD;
              import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.USERNAME;

              class KeycloakAuthClient {
                  Map<String, String> form(String user, String secret) {
                      return Map.of(GRANT_TYPE, PASSWORD, USERNAME, user, PASSWORD, secret);
                  }
              }
              """,
            """
              import java.util.Map;

              import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.GRANT_TYPE;

              class KeycloakAuthClient {
                  Map<String, String> form(String user, String secret) {
                      return Map.of(GRANT_TYPE, "password", "username", user, "password", secret);
                  }
              }
              """
          )
        );
    }
}

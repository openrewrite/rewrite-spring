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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateOpenSaml4ToOpenSaml5Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.security7.MigrateOpenSaml4ToOpenSaml5")
          .parser(JavaParser.fromJavaVersion().dependsOn(
            //language=java
            """
              package org.springframework.security.saml2.provider.service.authentication;
              import java.util.function.Function;
              public final class OpenSaml4AuthenticationProvider {
                  public static Function<ResponseToken, Object> createDefaultResponseAuthenticationConverter() { return null; }
                  public void setResponseAuthenticationConverter(Function<ResponseToken, ?> converter) {}
                  public static class ResponseToken {
                  }
              }
              """
          ));
    }

    @DocumentExample
    @Test
    void migrateAuthenticationProviderAndNestedResponseToken() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
              import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider.ResponseToken;

              class SecurityConfig {
                  OpenSaml4AuthenticationProvider authenticationProvider() {
                      OpenSaml4AuthenticationProvider authenticationProvider = new OpenSaml4AuthenticationProvider();
                      Function<ResponseToken, Object> delegate =
                              OpenSaml4AuthenticationProvider.createDefaultResponseAuthenticationConverter();
                      authenticationProvider.setResponseAuthenticationConverter(delegate);
                      return authenticationProvider;
                  }
              }
              """,
            """
              import java.util.function.Function;

              import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
              import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider.ResponseToken;

              class SecurityConfig {
                  OpenSaml5AuthenticationProvider authenticationProvider() {
                      OpenSaml5AuthenticationProvider authenticationProvider = new OpenSaml5AuthenticationProvider();
                      Function<ResponseToken, Object> delegate =
                              OpenSaml5AuthenticationProvider.createDefaultResponseAuthenticationConverter();
                      authenticationProvider.setResponseAuthenticationConverter(delegate);
                      return authenticationProvider;
                  }
              }
              """
          )
        );
    }
}

/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.springdoc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SecurityContextToSecuritySchemeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new SecurityContextToSecurityScheme())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "springfox-core-3.+")
          );
    }

    @DocumentExample
    @Test
    void apiKeyToSecurityScheme() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.service.ApiKey;

              class Test {
                  ApiKey apiKey() {
                      return new ApiKey("api_key", "X-API-KEY", "header");
                  }
              }
              """,
            """
              import io.swagger.v3.oas.models.security.SecurityScheme;

              class Test {
                  SecurityScheme apiKey() {
                      return new SecurityScheme()
                              .type(SecurityScheme.Type.APIKEY)
                              .name("X-API-KEY")
                              .in(SecurityScheme.In.HEADER);
                  }
              }
              """
          )
        );
    }

    @Test
    void authorizationScopeToScopes() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.service.AuthorizationScope;

              class Test {
                  AuthorizationScope authorizationScope() {
                      return new AuthorizationScope("global", "global scope");
                  }
              }
              """,
            """
              import io.swagger.v3.oas.models.security.Scopes;

              class Test {
                  Scopes authorizationScope() {
                      return new Scopes().addString("global", "global scope");
                  }
              }
              """
          )
        );
    }

    @Test
    void authorizationScopeListOfToChainedScopes() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.service.AuthorizationScope;
              import java.util.List;

              class Test {
                  List<AuthorizationScope> authorizationScopes() {
                      return List.of(
                          new AuthorizationScope("read", "Read access"),
                          new AuthorizationScope("write", "Write access"),
                          new AuthorizationScope("admin", "Admin access")
                      );
                  }
              }
              """,
            """
              import io.swagger.v3.oas.models.security.Scopes;

              class Test {
                  Scopes authorizationScopes() {
                      return new Scopes()
                              .addString("read", "Read access")
                              .addString("write", "Write access")
                              .addString("admin", "Admin access");
                  }
              }
              """
          )
        );
    }

    @Test
    void authorizationScopeArraysAsListToChainedScopes() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.service.AuthorizationScope;
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  List<AuthorizationScope> authorizationScopes() {
                      return Arrays.asList(
                          new AuthorizationScope("read", "Read access"),
                          new AuthorizationScope("write", "Write access"),
                          new AuthorizationScope("admin", "Admin access")
                      );
                  }
              }
              """,
            """
              import io.swagger.v3.oas.models.security.Scopes;

              class Test {
                  Scopes authorizationScopes() {
                      return new Scopes()
                              .addString("read", "Read access")
                              .addString("write", "Write access")
                              .addString("admin", "Admin access");
                  }
              }
              """
          )
        );
    }

    @Test
    void authorizationScopeArrayInitializerToChainedScopes() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.service.AuthorizationScope;

              class Test {
                  AuthorizationScope[] authorizationScopes() {
                      return new AuthorizationScope[] {
                          new AuthorizationScope("read", "Read access"),
                          new AuthorizationScope("write", "Write access"),
                          new AuthorizationScope("admin", "Admin access")
                      };
                  }
              }
              """,
            """
              import io.swagger.v3.oas.models.security.Scopes;

              class Test {
                  Scopes authorizationScopes() {
                      return new Scopes()
                              .addString("read", "Read access")
                              .addString("write", "Write access")
                              .addString("admin", "Admin access");
                  }
              }
              """
          )
        );
    }

    @Test
    void securityReferenceToSecurityRequirement() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.service.AuthorizationScope;
              import springfox.documentation.service.SecurityReference;
              import java.util.Arrays;

              class Test {
                  SecurityReference securityReference() {
                      return new SecurityReference("petstore-auth", new AuthorizationScope[] {
                          new AuthorizationScope("read:pets", "Read access"),
                          new AuthorizationScope("write:pets", "Write access")
                      });
                  }
              }
              """,
            """
              import io.swagger.v3.oas.models.security.SecurityRequirement;
              import java.util.Arrays;

              class Test {
                  SecurityRequirement securityReference() {
                      return new SecurityRequirement().addList("petstore-auth", Arrays.asList("read:pets", "write:pets"));
                  }
              }
              """
          )
        );
    }

    @Test
    void securityReferenceToSecurityRequirementAuthScopeArrayArg() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.service.AuthorizationScope;
              import springfox.documentation.service.SecurityReference;
              import java.util.Arrays;

              class Test {
                  SecurityReference securityReference(AuthorizationScope[] scopes) {
                      return new SecurityReference("petstore-auth", scopes);
                  }
              }
              """,
            """
              import io.swagger.v3.oas.models.security.Scopes;
              import io.swagger.v3.oas.models.security.SecurityRequirement;
              import java.util.Arrays;
              import java.util.stream.Collectors;

              class Test {
                  SecurityRequirement securityReference(Scopes scopes) {
                      return new SecurityRequirement().addList("petstore-auth", scopes.keySet().stream().collect(Collectors.toList()));
                  }
              }
              """
          )
        );
    }

}

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
package org.openrewrite.java.spring.security6;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot2.HeadersConfigurerLambdaDsl;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigratePermissionsPolicyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new HeadersConfigurerLambdaDsl(), Environment.builder().scanRuntimeClasspath().build()
            .activateRecipes("org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_4"))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-beans-5", "spring-context-5", "spring-security-config-5.8.+",
            "spring-security-web-5.8.+", "spring-web-5.3", "spring-core-5"));
    }

    @Test
    void keepsFollowingHeaderConfigurationOnHeadersConfigurer() {
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              class Security {
                  void configure(HttpSecurity http) throws Exception {
                      http.headers(headers -> headers
                          .permissionsPolicy().policy("camera=()").and()
                          .frameOptions().sameOrigin());
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              class Security {
                  void configure(HttpSecurity http) throws Exception {
                      http.headers(headers -> headers
                              .permissionsPolicyHeader(policy -> policy.policy("camera=()"))
                              .frameOptions(options -> options.sameOrigin()));
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesUnrelatedMethodAlone() {
        rewriteRun(java(
          """
            class Other {
                void permissionsPolicy(String value) {}
                void configure() { permissionsPolicy("camera=()"); }
            }
            """
        ));
    }
}

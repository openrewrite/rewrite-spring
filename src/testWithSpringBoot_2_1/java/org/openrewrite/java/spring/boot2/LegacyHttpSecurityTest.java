/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class LegacyHttpSecurityTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HttpSecurityLambdaDsl())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-beans", "spring-context", "spring-boot", "spring-security", "spring-web", "spring-core"));
    }

    @Test
    void dontUseUnavailableMethods() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeRequests()
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated();
                  }
              }
              """
          )
        );
    }
}

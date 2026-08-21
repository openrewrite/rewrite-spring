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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeSpringFramework_7_0Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.UpgradeSpringFramework_7_0")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.2"));
    }

    @DocumentExample
    @Test
    void replacesUnprocessableEntityStaticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

              class A {
                  int status() {
                      return UNPROCESSABLE_ENTITY.value();
                  }
              }
              """,
            """
              import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

              class A {
                  int status() {
                      return UNPROCESSABLE_CONTENT.value();
                  }
              }
              """
          )
        );
    }
}

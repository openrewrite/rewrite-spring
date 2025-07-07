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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateSpringAssertTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.MigrateSpringAssert")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-core-5"));
    }

    @DocumentExample
    @Test
    void migrateSpringAssert() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void test() {
                      org.springframework.util.Assert.state(true);
                      org.springframework.util.Assert.isTrue(true);
                  }
              }
              """,
            """
              class A {
                  void test() {
                      org.springframework.util.Assert.state(true, "must be true");
                      org.springframework.util.Assert.isTrue(true, "must be true");
                  }
              }
              """
          )
        );
    }
}

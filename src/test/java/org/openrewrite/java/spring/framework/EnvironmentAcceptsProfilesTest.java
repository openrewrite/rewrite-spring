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

@SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
class EnvironmentAcceptsProfilesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EnvironmentAcceptsProfiles())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-core-5.+"));
    }

    @DocumentExample
    @Test
    void acceptsSingleProfile() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.core.env.Environment;

              class Test {
                  static boolean method(Environment env) {
                      return env.acceptsProfiles("production");
                  }
              }
              """,
            """
              import org.springframework.core.env.Environment;
              import org.springframework.core.env.Profiles;

              class Test {
                  static boolean method(Environment env) {
                      return env.acceptsProfiles(Profiles.of("production"));
                  }
              }
              """
          )
        );
    }

    @Test
    void acceptsMultipleProfiles() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.core.env.Environment;

              class Test {
                  static boolean method(Environment env) {
                      return env.acceptsProfiles("test", "integration");
                  }
              }
              """,
            """
              import org.springframework.core.env.Environment;
              import org.springframework.core.env.Profiles;

              class Test {
                  static boolean method(Environment env) {
                      return env.acceptsProfiles(Profiles.of("test", "integration"));
                  }
              }
              """
          )
        );
    }
}

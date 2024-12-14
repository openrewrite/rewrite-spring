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
package org.openrewrite.java.spring.test;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringRulesToJUnitExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringRulesToJUnitExtension())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-boot-test", "spring-test")
            .dependsOn("package org.junit; public @interface ClassRule {}", "package org.junit; public @interface Rule {}")
          );
    }

    @Test
    @DocumentExample
    void migrateWithSpringBootTestPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.context.SpringBootTest;
              import org.springframework.test.context.junit4.rules.SpringClassRule;
              import org.springframework.test.context.junit4.rules.SpringMethodRule;
              import org.junit.ClassRule;
              import org.junit.Rule;

              @SpringBootTest
              class SomeTest {

                  @ClassRule
                  public static final SpringClassRule springClassRule = new SpringClassRule();

                  @Rule
                  public final SpringMethodRule springMethodRule = new SpringMethodRule();

              }
              """,
            """
              import org.springframework.boot.test.context.SpringBootTest;

              @SpringBootTest
              class SomeTest {

              }
              """
          )
        );
    }

    @Test
    void migrateSingleAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.context.SpringBootTest;
              import org.springframework.test.context.junit4.rules.SpringMethodRule;
              import org.junit.Rule;

              @SpringBootTest
              class SomeTest {

                  @Rule
                  public final SpringMethodRule springMethodRule = new SpringMethodRule();

              }
              """,
            """
              import org.springframework.boot.test.context.SpringBootTest;

              @SpringBootTest
              class SomeTest {

              }
              """
          )
        );
    }

    @Test
    void migrateAndAddSpringExtension() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.junit4.rules.SpringClassRule;
              import org.springframework.test.context.junit4.rules.SpringMethodRule;
              import org.junit.ClassRule;
              import org.junit.Rule;

              class SomeTest {

                  @ClassRule
                  public static final SpringClassRule springClassRule = new SpringClassRule();

                  @Rule
                  public final SpringMethodRule springMethodRule = new SpringMethodRule();

              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;

              @ExtendWith(SpringExtension.class)
              class SomeTest {

              }
              """
          )
        );
    }
}

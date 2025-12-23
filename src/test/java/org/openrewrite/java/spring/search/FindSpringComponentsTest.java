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
package org.openrewrite.java.spring.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.table.SpringComponentRelationships;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class FindSpringComponentsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringComponents())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-context-5.+"));
    }

    @DocumentExample
    @Test
    void findSpringComponents() {
        rewriteRun(
          spec -> spec.dataTable(SpringComponentRelationships.Row.class, rows ->
            assertThat(rows)
              .satisfiesExactlyInAnyOrder(one ->
                  assertThat(one)
                    .extracting(
                      SpringComponentRelationships.Row::getSourceFile,
                      SpringComponentRelationships.Row::getDependantType,
                      SpringComponentRelationships.Row::getDependencyType)
                    .contains("test/Config.java", "test.A", "test.B"),
                two ->
                  assertThat(two)
                    .extracting(
                      SpringComponentRelationships.Row::getSourceFile,
                      SpringComponentRelationships.Row::getDependantType,
                      SpringComponentRelationships.Row::getDependencyType)
                    .contains("test/C.java", "test.C", "test.B"))),
          //language=java
          java("package test; public class B {}"),
          //language=java
          java(
            """
              package test;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;

              @Configuration
              class Config {
                  @Bean
                  A a(B b) {
                      return new A(b);
                  }
              }
              """,
            """
              package test;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;

              @Configuration
              class Config {
                  /*~~(bean)~~>*/@Bean
                  A a(B b) {
                      return new A(b);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package test;

              class A {
                  public A(B b) {}
              }
              """
          ),
          //language=java
          java(
            """
              package test;
              import org.springframework.stereotype.Component;

              @Component
              class C {
                  public C(B b) {}
              }
              """,
            """
              package test;
              import org.springframework.stereotype.Component;

              /*~~(component)~~>*/@Component
              class C {
                  public C(B b) {}
              }
              """
          )
        );
    }
}

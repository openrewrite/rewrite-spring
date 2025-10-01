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

class BeanMethodReturnNullTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new BeanMethodReturnNull())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-context-5.+"));
    }

    @DocumentExample
    @Test
    void transformReturnType() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              class Test {
                  @Bean
                  public void myBean() {
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;

              class Test {
                  @Bean
                  public Object myBean() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void transformEachReturnInMethod() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              class Test {
                  @Bean
                  public void bar() {
                      if (true) {
                          return;
                      } else {
                          return;
                      }
                      int i = 1;
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;

              class Test {
                  @Bean
                  public Object bar() {
                      if (true) {
                          return null;
                      } else {
                          return null;
                      }
                      int i = 1;
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotTransformNestedReturn() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              class Test {
                  @Bean
                  public void myBean() {
                      Runnable r1 = () -> {
                          return;
                      };
                      Runnable r2 = new Runnable() {
                          @Override
                          public void run() {
                              return;
                          }
                      };
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;

              class Test {
                  @Bean
                  public Object myBean() {
                      Runnable r1 = () -> {
                          return;
                      };
                      Runnable r2 = new Runnable() {
                          @Override
                          public void run() {
                              return;
                          }
                      };
                      return null;
                  }
              }
              """
          )
        );
    }
}

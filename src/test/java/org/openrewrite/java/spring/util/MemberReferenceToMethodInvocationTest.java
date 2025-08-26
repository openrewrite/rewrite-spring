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
package org.openrewrite.java.spring.util;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class MemberReferenceToMethodInvocationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(MemberReferenceToMethodInvocation::new))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-core-6"));
    }

    @DocumentExample
    @Test
    void singleArgWithoutParameterNameRetained() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.concurrent.ListenableFuture;
              class A {
                  void test(ListenableFuture<String> future) {
                      future.addCallback(
                          System.out::println,
                          System.err::println);
                  }
              }
              """,
            """
              import org.springframework.util.concurrent.ListenableFuture;
              class A {
                  void test(ListenableFuture<String> future) {
                      future.addCallback(
                          (String x) -> System.out.println(x),
                          (Object x) -> System.err.println(x));
                  }
              }
              """
          )
        );
    }

    @Test
    void methodReferenceWithoutArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Optional;

              class A {
                  void test() {
                      byte[] result = Optional.of("Test").map(String::getBytes).orElse(null);
                  }
              }
              """,
            """
              import java.util.Optional;

              class A {
                  void test() {
                      byte[] result = Optional.of("Test").map((String string) -> string.getBytes()).orElse(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void thisMethodReferenceWithoutArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Optional;
              import java.util.function.Supplier;

              class A {
                  public Supplier<String> getString() {
                      return super::toString;
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.function.Supplier;

              class A {
                  public Supplier<String> getString() {
                      return () -> super.toString();
                  }
              }
              """
          )
        );
    }
}

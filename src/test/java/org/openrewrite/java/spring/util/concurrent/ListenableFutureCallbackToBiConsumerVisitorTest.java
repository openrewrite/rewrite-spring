/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.util.concurrent;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class ListenableFutureCallbackToBiConsumerVisitorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(ListenableFutureCallbackToBiConsumerVisitor::new))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-core-6"));
    }

    @DocumentExample
    @Test
    void classThatImplementsListenableFutureCallback() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.concurrent.ListenableFutureCallback;
              
              class MyCallback implements ListenableFutureCallback<String> {
              
                  private String greeting = "Hi ";
              
                  @Override
                  public void onSuccess(String result) {
                      System.out.println(greeting + result);
                  }
              
                  @Override
                  public void onFailure(Throwable ex) {
                      System.err.println(ex.getMessage());
                  }
              }
              """,
            """
              import java.util.function.BiConsumer;
              
              class MyCallback implements BiConsumer<String, Throwable> {
              
                  private String greeting = "Hi ";
              
                  @Override
                  public void accept(String result, Throwable ex) {
                      if (ex == null) {
                          System.out.println(greeting + result);
                      } else {
                          System.err.println(ex.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousClassToLambda() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.concurrent.ListenableFutureCallback;
              
              class Holder {
                  Object callback = new ListenableFutureCallback<String>() {
                      @Override
                      public void onSuccess(String result) {
                          System.out.println(result);
                      }
                      @Override
                      public void onFailure(Throwable ex) {
                          System.err.println(ex.getMessage());
                      }
                  };
              }
              """,
            """
              class Holder {
                  Object callback = (String result, Throwable ex) -> {
                      if (ex == null) {
                          System.out.println(result);
                      } else {
                          System.err.println(ex.getMessage());
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void anonymousClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.concurrent.ListenableFutureCallback;
              
              class Holder {
                  Object callback = new ListenableFutureCallback<String>() {
              
                      private String greeting = "Hi ";
              
                      @Override
                      public void onSuccess(String result) {
                          System.out.println(greeting + result);
                      }
                      @Override
                      public void onFailure(Throwable ex) {
                          System.err.println(ex.getMessage());
                      }
                  };
              }
              """,
            """
              import java.util.function.BiConsumer;
              
              class Holder {
                  Object callback = new BiConsumer<String, Throwable>() {
              
                      private String greeting = "Hi ";
              
                      @Override
                      public void accept(String result, Throwable ex) {
                          if (ex == null) {
                              System.out.println(greeting + result);
                          } else {
                              System.err.println(ex.getMessage());
                          }
                      }
                  };
              }
              """
          )
        );
    }
}

/*
 * Copyright 2025 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddSpringExtensionConfigForNestedTestsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddSpringExtensionConfigForNestedTests())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api", "spring-test-5.3.+")
            .dependsOn(
              // Type stub for Spring 7's @SpringExtensionConfig
              """
                package org.springframework.test.context.junit.jupiter;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                @Documented
                @Inherited
                public @interface SpringExtensionConfig {
                    boolean useTestClassScopedExtensionContext();
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void addSpringExtensionConfigToTestWithNestedClasses() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;

              @ExtendWith(SpringExtension.class)
              class MyTest {

                  @Test
                  void outerTest() {
                  }

                  @Nested
                  class NestedTests {
                      @Test
                      void innerTest() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;
              import org.springframework.test.context.junit.jupiter.SpringExtensionConfig;

              @ExtendWith(SpringExtension.class)
              @SpringExtensionConfig(useTestClassScopedExtensionContext = true)
              class MyTest {

                  @Test
                  void outerTest() {
                  }

                  @Nested
                  class NestedTests {
                      @Test
                      void innerTest() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void addSpringExtensionConfigWithMultipleNestedClasses() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;

              @ExtendWith(SpringExtension.class)
              class MyTest {

                  @Nested
                  class FirstNestedTests {
                      @Test
                      void test1() {
                      }
                  }

                  @Nested
                  class SecondNestedTests {
                      @Test
                      void test2() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;
              import org.springframework.test.context.junit.jupiter.SpringExtensionConfig;

              @ExtendWith(SpringExtension.class)
              @SpringExtensionConfig(useTestClassScopedExtensionContext = true)
              class MyTest {

                  @Nested
                  class FirstNestedTests {
                      @Test
                      void test1() {
                      }
                  }

                  @Nested
                  class SecondNestedTests {
                      @Test
                      void test2() {
                      }
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void noNestedClasses() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.springframework.test.context.junit.jupiter.SpringExtension;

                  @ExtendWith(SpringExtension.class)
                  class MyTest {

                      @Test
                      void test() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noSpringExtension() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Nested;
                  import org.junit.jupiter.api.Test;

                  class MyTest {

                      @Nested
                      class NestedTests {
                          @Test
                          void test() {
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void alreadyHasSpringExtensionConfig() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Nested;
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.springframework.test.context.junit.jupiter.SpringExtension;
                  import org.springframework.test.context.junit.jupiter.SpringExtensionConfig;

                  @ExtendWith(SpringExtension.class)
                  @SpringExtensionConfig(useTestClassScopedExtensionContext = true)
                  class MyTest {

                      @Nested
                      class NestedTests {
                          @Test
                          void test() {
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void innerClassWithoutNestedAnnotation() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.springframework.test.context.junit.jupiter.SpringExtension;

                  @ExtendWith(SpringExtension.class)
                  class MyTest {

                      @Test
                      void test() {
                      }

                      // This is not a @Nested test class, just a helper class
                      class HelperClass {
                      }
                  }
                  """
              )
            );
        }
    }
}

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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AutowiredFieldsIntoRequiredArgsConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AutowiredFieldsIntoRequiredArgsConstructor())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-beans-5.+")
            .dependsOn(
              """
                package lombok;
                public @interface RequiredArgsConstructor {}
                """
            ));
    }

    @DocumentExample
    @Test
    void fieldIntoNewRequiredArgsConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private String a;

              }
              """,
            """
              package demo;

              import lombok.RequiredArgsConstructor;

              @RequiredArgsConstructor
              public class A {

                  private final String a;

              }
              """
          )
        );
    }

    @Test
    void multipleAutowiredFieldsReplaceEmptyConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private String a;

                  @Autowired
                  private String b;

                  A() {
                  }

              }
              """,
            """
              package demo;

              import lombok.RequiredArgsConstructor;

              @RequiredArgsConstructor
              public class A {

                  private final String a;

                  private final String b;

              }
              """
          )
        );
    }

    @Test
    void keepsAlreadyFinalInitializedFields() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  private static final String CONST = "x";

                  private final String initialized = "y";

                  @Autowired
                  private String a;

              }
              """,
            """
              package demo;

              import lombok.RequiredArgsConstructor;

              @RequiredArgsConstructor
              public class A {

                  private static final String CONST = "x";

                  private final String initialized = "y";

                  private final String a;

              }
              """
          )
        );
    }

    @Test
    void doesNotTouchUnrelatedMutableField() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  private String b;

                  @Autowired
                  private String a;

              }
              """
          )
        );
    }

    @Test
    void doesNotTouchNonEmptyConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private String a;

                  A() {
                      System.out.println("hi");
                  }

              }
              """
          )
        );
    }

    @Test
    void noAutowiredField() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  private String a;

              }
              """
          )
        );
    }

    @Test
    void alreadyHasLombokConstructorAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import lombok.RequiredArgsConstructor;
              import org.springframework.beans.factory.annotation.Autowired;

              @RequiredArgsConstructor
              public class A {

                  @Autowired
                  private String a;

              }
              """
          )
        );
    }
}

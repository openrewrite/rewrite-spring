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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

/**
 * @author Alex Boyko
 */
class AutowiredFieldIntoConstructorParameterVisitorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new AutowiredFieldIntoConstructorParameterVisitor("demo.A", "a")))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans-5.+"));
    }

    @DocumentExample
    @Test
    void fieldIntoExistingSingleConstructor() {
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
                  }

              }
              """,
            """
              package demo;

              public class A {

                  private final String a;

                  A(String a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldIntoExistingSingleConstructor2() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  String a;

                  A() {
                  }

              }
              """,
            """
              package demo;

              public class A {

                  final String a;

                  A(String a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldIntoNewConstructor() {
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

              public class A {

                  private final String a;

                  A(String a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldIntoAutowiredConstructor() {
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
                  }

                  @Autowired
                  A(long l) {
                  }
              }
              """,
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  private final String a;

                  A() {
                  }

                  @Autowired
                  A(long l, String a) {
                      this.a = a;
                  }
              }
              """
          )
        );
    }

    @Test
    void noAutowiredConstructor() {
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
                  }

                  A(long l) {
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

                  A() {
                  }

              }
              """
          )
        );
    }

    @Test
    void multipleAutowiredConstructors() {
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
                  A() {
                  }

                  @Autowired
                  A(long l) {
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldIntoAutowiredConstructorInnerClassPresent() {
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
                  }

                  public static class B {

                      @Autowired
                      private String a;

                      B() {

                      }
                  }

              }
              """,
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  private final String a;

                  A(String a) {
                      this.a = a;
                  }

                  public static class B {

                      @Autowired
                      private String a;

                      B() {

                      }
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldInitializedInConstructor() {
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
                      this.a = "something";
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldInitializedInConstructorWithoutThis() {
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
                      a = "something";
                  }

              }
              """
          )
        );
    }

    @Test
    void finalFieldIntoExistingSingleConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  final private String a;

                  A() {
                  }

              }
              """,
            """
              package demo;

              public class A {

                  final private String a;

                  A(String a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithGenericTypeIntoNewConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import java.util.List;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private List<String> a;

              }
              """,
            """
              package demo;

              import java.util.List;

              public class A {

                  private final List<String> a;

                  A(List<String> a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithGenericTypeIntoExistingConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import java.util.List;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private List<String> a;

                  A() {
                  }

              }
              """,
            """
              package demo;

              import java.util.List;

              public class A {

                  private final List<String> a;

                  A(List<String> a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithGenericTypeIntoExistingConstructorWithParams() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import java.util.List;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private List<String> a;

                  A() {
                  }

                  @Autowired
                  A(long l) {
                  }
              }
              """,
            """
              package demo;

              import java.util.List;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  private final List<String> a;

                  A() {
                  }

                  @Autowired
                  A(long l, List<String> a) {
                      this.a = a;
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldWithNestedGenericType() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import java.util.List;
              import java.util.Map;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private Map<String, List<Integer>> a;

              }
              """,
            """
              package demo;

              import java.util.List;
              import java.util.Map;

              public class A {

                  private final Map<String, List<Integer>> a;

                  A(Map<String, List<Integer>> a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithWildcardGenericType() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import java.util.List;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private List<?> a;

              }
              """,
            """
              package demo;

              import java.util.List;

              public class A {

                  private final List<?> a;

                  A(List<?> a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithUpperBoundedWildcard() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import java.util.List;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private List<? extends Number> a;

              }
              """,
            """
              package demo;

              import java.util.List;

              public class A {

                  private final List<? extends Number> a;

                  A(List<? extends Number> a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithUserDefinedGenericType() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.none()),
          java(
            """
              package demo.model;

              public class MyConfig {
              }
              """
          ),
          java(
            """
              package demo.service;

              public class MyService<T> {
              }
              """
          ),
          java(
            """
              package demo;

              import demo.model.MyConfig;
              import demo.service.MyService;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private MyService<MyConfig> a;

              }
              """,
            """
              package demo;

              import demo.model.MyConfig;
              import demo.service.MyService;

              public class A {

                  private final MyService<MyConfig> a;

                  A(MyService<MyConfig> a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithLowerBoundedWildcard() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import java.util.List;
              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private List<? super Integer> a;

              }
              """,
            """
              package demo;

              import java.util.List;

              public class A {

                  private final List<? super Integer> a;

                  A(List<? super Integer> a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithArrayType() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.all().identifiers(false)),
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired
                  private String[] a;

              }
              """,
            """
              package demo;

              public class A {

                  private final String[] a;

                  A(String[] a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithAutowiredRequired() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;

              public class A {

                  @Autowired(required = false)
                  private String a;

                  A() {
                  }

              }
              """,
            """
              package demo;

              public class A {

                  private final String a;

                  A(String a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithMultipleAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;

              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;

              public class A {

                  @Autowired
                  @Qualifier("myBean")
                  private String a;

                  A() {
                  }

              }
              """,
            """
              package demo;

              import org.springframework.beans.factory.annotation.Qualifier;

              public class A {

                  @Qualifier("myBean")
                  private final String a;

                  A(String a) {
                      this.a = a;
                  }

              }
              """
          )
        );
    }

}

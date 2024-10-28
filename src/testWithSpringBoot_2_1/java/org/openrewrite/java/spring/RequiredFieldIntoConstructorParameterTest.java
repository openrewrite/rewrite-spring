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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RequiredFieldIntoConstructorParameterTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RequiredFieldIntoConstructorParameter())
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans", "lombok"));
    }

    @DocumentExample
    @Test
    void fieldIntoExistingSingleConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.beans.factory.annotation.Required;
              
              class Foo {
                  private String a;
              
                  @Required
                  void setA(String a) {
                      this.a = a;
                  }
              }
              """,
            """
              class Foo {
                  private String a;
              
                  Foo(String a) {
                      this.a = a;
                  }
              
                  void setA(String a) {
                      this.a = a;
                  }
              }
              """
          )
        );
    }

    @Test
    void currentConstructorsShouldNotBeChanged() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Required;
              
              class Foo {
                 private String first;
                 private String a;
              
                 Foo(String first) {
                    this.first = first;
                 }
              
                 @Required
                 void setA(String a) {
                     this.a = a;
                 }
              }
              """
          )
        );
    }

    @Test
    void inherentedFieldsAreNotConsidered() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Required;
              
              class Bar {
                  String a;
              }
              
              class Foo extends Bar {
                  @Required
                  void setA(String a) {
                      this.a = a;
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleRequiredFieldIntoConstructorParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Required;
              
              class Foo {
                  private String a;
                  private String b;
              
                  @Required
                  void setA(String a) {
                      this.a = a;
                  }
              
                  @Required
                  void setB(String b) {
                      this.b = b;
                  }
              }
              """,
            """
              class Foo {
                  private String a;
                  private String b;
              
                  Foo(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }
              
                  void setA(String a) {
                      this.a = a;
                  }
              
                  void setB(String b) {
                      this.b = b;
                  }
              }
              """
          )
        );
    }

    @Test
    void explicitEmptyConstructorShouldNotBeModified() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Required;
              
              class Foo {
                  private String a;
              
                  Foo() {
                  }
              
                  @Required
                  void setA(String a) {
                      this.a = a;
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyRemoveAnnotationsForSingleSetter() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Required;
              
              class Foo {
                  private String a;
                  private String b;
              
                  Foo(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }
              
                  @Required
                  void setA(String a) {
                      this.a = a;
                  }
              
                  void setB(String b) {
                      this.b = b;
                  }
              }
              """,
            """
              class Foo {
                  private String a;
                  private String b;
              
                  Foo(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }
              
                  void setA(String a) {
                      this.a = a;
                  }
              
                  void setB(String b) {
                      this.b = b;
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyRemoveAnnotationsMultipleSetters() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Required;
              
              class Foo {
                  private String a;
                  private String b;
              
                  Foo(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }
              
                  @Required
                  void setA(String a) {
                      this.a = a;
                  }
              
                  @Required
                  void setB(String b) {
                      this.b = b;
                  }
              }
              """,
            """
              class Foo {
                  private String a;
                  private String b;
              
                  Foo(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }
              
                  void setA(String a) {
                      this.a = a;
                  }
              
                  void setB(String b) {
                      this.b = b;
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "AllArgsConstructor",
      "RequiredArgsConstructor",
      "NoArgsConstructor",
      "Data",
      "Value"
    })
    void ignoreLombok(String annotation) {
        rewriteRun(
          //language=java
          java(
            """
              import lombok.%1$s;
              import org.springframework.beans.factory.annotation.Required;
              
              @%1$s
              class Foo {
                  private String a;
                  private String b;
              
                  @Required
                  void setA(String a) {
                      this.a = a;
                  }
              
                  @Required
                  void setB(String b) {
                      this.b = b;
                  }
              }
              """.formatted(annotation)
          )
        );
    }

}

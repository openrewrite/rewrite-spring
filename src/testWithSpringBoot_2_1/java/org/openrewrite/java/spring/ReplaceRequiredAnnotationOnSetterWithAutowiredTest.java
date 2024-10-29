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
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceRequiredAnnotationOnSetterWithAutowiredTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceRequiredAnnotationOnSetterWithAutowired())
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans"));
    }

    @DocumentExample
    @Test
    void replaceRequiredWithAutowired() {
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
              import org.springframework.beans.factory.annotation.Autowired;
              
              class Foo {
                  private String a;
              
                  @Autowired
                  void setA(String a) {
                      this.a = a;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceRequiredWithAutowiredMultipleSetters() {
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
              import org.springframework.beans.factory.annotation.Autowired;
              
              class Foo {
                  private String a;
                  private String b;
              
                  @Autowired
                  void setA(String a) {
                      this.a = a;
                  }
              
                  @Autowired
                  void setB(String b) {
                      this.b = b;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceRequiredWithAutowiredMultipleSettersNotAllRequired() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Required;
              
              class Foo {
                  private String a;
                  private String b;
              
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
              import org.springframework.beans.factory.annotation.Autowired;
              
              class Foo {
                  private String a;
                  private String b;
              
                  void setA(String a) {
                      this.a = a;
                  }
              
                  @Autowired
                  void setB(String b) {
                      this.b = b;
                  }
              }
              """
          )
        );
    }

    @Test
    void setterWithoutAnnotationsShouldNotCauseChanges() {
        rewriteRun(
          //language=java
          java(
            """
                class Foo {
                  private String a;
              
                  void setA(String a) {
                      this.a = a;
                  }
              }
              """
          )
        );
    }
}

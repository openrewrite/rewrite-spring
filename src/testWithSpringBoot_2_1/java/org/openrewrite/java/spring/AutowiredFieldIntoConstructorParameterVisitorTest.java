/*
 * Copyright 2022 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

/**
 * @author Alex Boyko
 */
class AutowiredFieldIntoConstructorParameterVisitorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new AutowiredFieldIntoConstructorParameterVisitor("demo.A", "a")))
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans"));
    }

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

}

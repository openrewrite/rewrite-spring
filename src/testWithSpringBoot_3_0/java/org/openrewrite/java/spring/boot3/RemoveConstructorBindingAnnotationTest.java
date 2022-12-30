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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * @author Alex Boyko
 */
class RemoveConstructorBindingAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveConstructorBindingAnnotation())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot"));
    }

    @Test
    void topLevelTypeAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
              
              @ConfigurationProperties
              @ConstructorBinding
              class A {
                  void method() {
                  }
              }
              """,
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              
              @ConfigurationProperties
              class A {
                  void method() {
                  }
              }
              """
          )
        );
    }

    @Test
    void topLevelClassAnnotationWithConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
              
              @ConfigurationProperties
              @ConstructorBinding
              class A {
                  A() {
                  }
              }
              """,
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              
              @ConfigurationProperties
              class A {
                  A() {
                  }
              }
              """
          )
        );
    }

    @Test
    void topLevelTypeAnnotationWithMultipleConstructors() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
                            
              @ConfigurationProperties
              @ConstructorBinding
              class A {
                  A() {
                  }
                  A(int n) {
                  }
              }
              """,
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
                            
              @ConfigurationProperties
              /**
               * TODO:
               * You need to remove ConstructorBinding on class level and move it to appropriate
               * constructor.
               */
              @ConstructorBinding
              class A {
                  A() {
                  }
                  A(int n) {
                  }
              }
              """
          )
        );
    }

    @Test
    void constructorAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
                            
              @ConfigurationProperties
              class A {
                  @ConstructorBinding
                  A() {
                  }
              }
              """,
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
                            
              @ConfigurationProperties
              class A {
                 \s
                  A() {
                  }
              }
              """
          )
        );
    }

    @Test
    void topLevelTypeAnnotationWithoutConfigProperties() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConstructorBinding;
                            
              @ConstructorBinding
              class A {
              }
              """
          )
        );
    }

    @Test
    void constructorAnnotationWithMultipleConstructors() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
                            
              @ConfigurationProperties
              class A {
                  A() {
                  }
                  @ConstructorBinding
                  A(int n) {
                  }
              }
              """
          )
        );
    }

    @Test
    void noConstrBindingAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
                            
              @ConfigurationProperties
              class A {
                  A() {
                  }
                  A(int n) {
                  }
              }
              """
          )
        );
    }

    @Test
    void topLevelTypeAnnotationInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
              
              class A {
                  void method() {
                  }
                  
                  @ConfigurationProperties
                  @ConstructorBinding
                  static class B {
                  }
              }
              """,
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              
              class A {
                  void method() {
                  }
                  
                  @ConfigurationProperties
                  static class B {
                  }
              }
              """
          )
        );
    }

    @Test
    void constructorAnnotationInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
              
              @ConfigurationProperties
              @ConstructorBinding
              class A {
                  void method() {
                  }
                  
                  @ConfigurationProperties
                  static class B {
                      @ConstructorBinding
                      B() {
                      }
                  }
              }
              """,
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              
              @ConfigurationProperties
              class A {
                  void method() {
                  }
                  
                  @ConfigurationProperties
                  static class B {
                     \s
                      B() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void constructorAndTypeAnnotationWithMultipleConstructorsInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
              
              class A {
                  @ConfigurationProperties
                  static class B {
                      B() {
                      }
                      @ConstructorBinding
                      B(int n) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeConstructorBindingFromInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
              
              class A {
                  @ConfigurationProperties
                  static class B {
                      @ConstructorBinding
                      B(int n) {
                      }
                  }
              }
              """,
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              
              class A {
                  @ConfigurationProperties
                  static class B {
                     \s
                      B(int n) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void addCommentToInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
              
              class A {
                  @ConfigurationProperties
                  @ConstructorBinding
                  static class B {
                      B() {
                      }
                      B(int n) {
                      }
                  }
              }
              """,
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.boot.context.properties.ConstructorBinding;
              
              class A {
                  @ConfigurationProperties
                  /**
                   * TODO:
                   * You need to remove ConstructorBinding on class level and move it to appropriate
                   * constructor.
                   */
                  @ConstructorBinding
                  static class B {
                      B() {
                      }
                      B(int n) {
                      }
                  }
              }
              """
          )
        );
    }
}

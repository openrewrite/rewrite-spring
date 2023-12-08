/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.concurrent.TimeUnit;

import static org.openrewrite.java.Assertions.java;

public class AddTimeUnitArgumentTest implements RewriteTest {
    //language=java
    private static final SourceSpecs stubCode = java(
            """
      import java.util.concurrent.TimeUnit;
      
      class A {
          private long value;
          private float foo;
          private TimeUnit timeunit;
          
          A method(int value) {
              this.value = value;
              this.timeunit = TimeUnit.MILLISECONDS;
              return this;
          }
          
          A method(long value, TimeUnit timeunit) {
              this.value = value;
              this.timeunit = timeunit;
              return this;
          }
          
          A method(int value, float foo) {
              this.value = value;
              this.foo = foo;
              this.timeunit = TimeUnit.MILLISECONDS;
              return this;
          }
          
          A method(long value, float foo, TimeUnit timeunit) {
              this.value = value;
              this.foo = foo;
              this.timeunit = timeunit;
              return this;
          }
      }
      """);

    @Test
    void addTimeUnitDefaultMilliseconds() {
        rewriteRun(
          spec -> spec.recipe(new AddTimeUnitArgument("A method(int)", null)),
          stubCode,
          //language=java
          java(
                """
            class B {
                void test() {
                    A a = new A();
                    a.method(100);
                }
            }
            """, """
            import java.util.concurrent.TimeUnit;
                        
            class B {
                void test() {
                    A a = new A();
                    a.method(100, TimeUnit.MILLISECONDS);
                }
            }
            """)
        );
    }

    @Test
    void addTimeUnitSpecificTimeUnit() {
        rewriteRun(
          spec -> spec.recipe(new AddTimeUnitArgument("A method(int)", TimeUnit.SECONDS)),
          stubCode,
          //language=java
          java(
                """
            class B {
                void test() {
                    A a = new A();
                    a.method(100);
                }
            }
            """, """
            import java.util.concurrent.TimeUnit;
                        
            class B {
                void test() {
                    A a = new A();
                    a.method(100, TimeUnit.SECONDS);
                }
            }
            """)
        );
    }

    @Test
    void doesModifyMethodsWithMoreThanOneArgument() {
        rewriteRun(
          spec -> spec.recipe(new AddTimeUnitArgument("A method(int, float)", null)),
          //language=java
          stubCode,
          //language=java
          java(
                """
            class B {
                void test() {
                    A a = new A();
                    a.method(100, 1.0f);
                }
            }
            """, """
            import java.util.concurrent.TimeUnit;
                        
            class B {
                void test() {
                    A a = new A();
                    a.method(100, 1.0f, TimeUnit.MILLISECONDS);
                }
            }
            """)
        );
    }
}

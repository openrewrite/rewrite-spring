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

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ChangeMethodParameterTest implements RewriteTest {

    @DocumentExample
    @Test
    void primitive() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "long", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """,
            """
              package foo;

              public class Foo {
                  public void bar(long i) {
                  }
              }
              """
          )
        );
    }

    @Test
    void indexLargeThanZero() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "long", 1)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i, int j) {
                  }
              }
              """,
            """
              package foo;

              public class Foo {
                  public void bar(int i, long j) {
                  }
              }
              """
          )
        );
    }

    @Test
    void sameType() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "int", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """
          )
        );
    }

    @Test
    void notExistsIndex() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "int", 1)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """
          )
        );
    }

    @Test
    void methodNotMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "long", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """,
            """
              package foo;

              public class Foo {
                  public void bar(long i) {
                  }
              }
              """
          )
        );
    }

    @Test
    void typePattern() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("*..*#bar(..)", "long", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """,
            """
              package foo;

              public class Foo {
                  public void bar(long i) {
                  }
              }
              """
          )
        );
    }

    @Test
    void primitiveArray() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "int[]", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """,
            """
              package foo;

              public class Foo {
                  public void bar(int[] i) {
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterized() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "java.util.List<java.util.regex.Pattern>", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """,
            """
              package foo;

              import java.util.List;
              import java.util.regex.Pattern;

              public class Foo {
                  public void bar(List<Pattern> i) {
                  }
              }
              """
          )
        );
    }

    @Test
    void wildcard() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "java.util.List<?>", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """,
            """
              package foo;

              import java.util.List;

              public class Foo {
                  public void bar(List<?> i) {
                  }
              }
              """
          )
        );
    }

    @Test
    void wildcardExtends() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "java.util.List<? extends Object>", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """,
            """
              package foo;

              import java.util.List;

              public class Foo {
                  public void bar(List<? extends Object> i) {
                  }
              }
              """
          )
        );
    }

    @Test
    void string() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "String", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int j) {
                  }

                  public void bar(int i) {
                  }

                  public void bar(int i, int j) {
                  }
              }
              """,
            """
              package foo;

              public class Foo {
                  public void bar(String j) {
                  }

                  public void bar(String i) {
                  }

                  public void bar(String i, int j) {
                  }
              }
              """
          )
        );
    }

    @Test
    void first() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "long", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }

                  public void bar(int i, int j) {
                  }
              }
              """,
            """
              package foo;

              public class Foo {
                  public void bar(long i) {
                  }

                  public void bar(long i, int j) {
                  }
              }
              """
          )
        );
    }

    @Test
    void qualified() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "java.util.regex.Pattern", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
                  public void bar(int i, int j) {
                  }
              }
              """,
            """
              package foo;

              import java.util.regex.Pattern;

              public class Foo {
                  public void bar(Pattern i) {
                  }

                  public void bar(Pattern i, int j) {
                  }
              }
              """
          )
        );
    }

    @Test
    void object() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("foo.Foo#bar(..)", "Object", 0)),
          //language=java
          java(
            """
              package foo;

              public class Foo {
                  public void bar(int i) {
                  }
              }
              """,
            """
              package foo;

              public class Foo {
                  public void bar(Object i) {
                  }
              }
              """
          )
        );
    }

    @Language("java")
    String b = """
      package b;
      public interface B {
         boolean m(String i);
         boolean m(String i, String j);
      }
      """;

    @Test
    void fromInterface() {
        rewriteRun(
          spec -> spec.recipe(new ChangeMethodParameter("b.B#m(String, String)", "long", 0)),
          //language=java
          java(b),
          java(
            """
              package foo;

              import b.*;

              public class Foo implements B {
                  @Override
                  public boolean m(String i) {
                      return true;
                  }

                  @Override
                  public boolean m(String i, String j) {
                      return true;
                  }
              }
              """,
            """
              package foo;

              import b.*;

              public class Foo implements B {
                  @Override
                  public boolean m(String i) {
                      return true;
                  }

                  @Override
                  public boolean m(long i, String j) {
                      return true;
                  }
              }
              """
          )
        );
    }
}

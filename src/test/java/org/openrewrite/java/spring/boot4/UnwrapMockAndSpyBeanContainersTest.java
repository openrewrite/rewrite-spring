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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class UnwrapMockAndSpyBeanContainersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnwrapMockAndSpyBeanContainers())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-test-3", "mockito-core-5"))
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-test-3", "mockito-core-5"));
    }

    @DocumentExample
    @Test
    void mergesMockBeansContainer() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.mock.mockito.MockBean;
              import org.springframework.boot.test.mock.mockito.MockBeans;

              @MockBeans({@MockBean(Foo.class), @MockBean(Bar.class)})
              class SomeTest {
              }
              class Foo {}
              class Bar {}
              """,
            """
              import org.springframework.boot.test.mock.mockito.MockBean;

              @MockBean(types = {Foo.class, Bar.class})
              class SomeTest {
              }
              class Foo {}
              class Bar {}
              """
          )
        );
    }

    @Test
    void mergesSpyBeansContainer() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.mock.mockito.SpyBean;
              import org.springframework.boot.test.mock.mockito.SpyBeans;

              @SpyBeans({@SpyBean(Foo.class), @SpyBean(Bar.class)})
              class SomeTest {
              }
              class Foo {}
              class Bar {}
              """,
            """
              import org.springframework.boot.test.mock.mockito.SpyBean;

              @SpyBean(types = {Foo.class, Bar.class})
              class SomeTest {
              }
              class Foo {}
              class Bar {}
              """
          )
        );
    }

    @Test
    void leavesUnrelatedAnnotationsAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.mock.mockito.MockBean;

              @MockBean(Foo.class)
              class SomeTest {
              }
              class Foo {}
              """
          )
        );
    }

    @Test
    void kotlinVariadicMockBeansWithClassLiteral() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.test.mock.mockito.MockBean
              import org.springframework.boot.test.mock.mockito.MockBeans

              @MockBeans(MockBean(Foo::class), MockBean(Bar::class))
              class SomeTest {
              }
              class Foo
              class Bar
              """,
            """
              import org.springframework.boot.test.mock.mockito.MockBean

              @MockBean(types = [Foo::class, Bar::class])
              class SomeTest {
              }
              class Foo
              class Bar
              """
          )
        );
    }

    @Test
    void kotlinSpyBeansVariadic() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.test.mock.mockito.SpyBean
              import org.springframework.boot.test.mock.mockito.SpyBeans

              @SpyBeans(SpyBean(Foo::class), SpyBean(Bar::class))
              class SomeTest {
              }
              class Foo
              class Bar
              """,
            """
              import org.springframework.boot.test.mock.mockito.SpyBean

              @SpyBean(types = [Foo::class, Bar::class])
              class SomeTest {
              }
              class Foo
              class Bar
              """
          )
        );
    }

    @Test
    void kotlinSingleInnerAnnotation() {
        // Kotlin requires an array literal for Array<KClass<*>> attributes —
        // the Java single-element shorthand does not compile (Sunbit, customer-requests#2435).
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.test.mock.mockito.MockBean
              import org.springframework.boot.test.mock.mockito.MockBeans

              @MockBeans(MockBean(Foo::class))
              class SomeTest {
              }
              class Foo
              """,
            """
              import org.springframework.boot.test.mock.mockito.MockBean

              @MockBean(types = [Foo::class])
              class SomeTest {
              }
              class Foo
              """
          )
        );
    }

    @Test
    void javaSingleInnerAnnotationKeepsShorthand() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.mock.mockito.MockBean;
              import org.springframework.boot.test.mock.mockito.MockBeans;

              @MockBeans(@MockBean(Foo.class))
              class SomeTest {
              }
              class Foo {}
              """,
            """
              import org.springframework.boot.test.mock.mockito.MockBean;

              @MockBean(types = Foo.class)
              class SomeTest {
              }
              class Foo {}
              """
          )
        );
    }

    @Test
    void kotlinNamedValueAttribute() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.test.mock.mockito.MockBean
              import org.springframework.boot.test.mock.mockito.MockBeans

              @MockBeans(value = [MockBean(Foo::class), MockBean(Bar::class)])
              class SomeTest {
              }
              class Foo
              class Bar
              """,
            """
              import org.springframework.boot.test.mock.mockito.MockBean

              @MockBean(types = [Foo::class, Bar::class])
              class SomeTest {
              }
              class Foo
              class Bar
              """
          )
        );
    }
}

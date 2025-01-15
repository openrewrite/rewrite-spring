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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceMockBeanAndSpyBeanTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.boot3.ReplaceMockBeanAndSpyBean")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-test", "mockito-core"));
    }

    @DocumentExample
    @Test
    void replacesMockBeanWithMockitoBean() {
        rewriteRun(
          // Input source file before applying the recipe
          java(
            """
              import org.springframework.boot.test.mock.mockito.MockBean;

              public class SomeTest {
                  @MockBean
                  private String someService;
              }
              """,
            // Expected output after applying the recipe
            """
              import org.springframework.test.context.bean.override.mockito.MockitoBean;

              public class SomeTest {
                  @MockitoBean
                  private String someService;
              }
              """
          )
        );
    }

    @Test
    void replacesMockBeanWithMockitoBeanWithAttributes() {
        rewriteRun(
          // Input source file before applying the recipe
          java(
            """
              import org.mockito.Answers;
              import org.springframework.boot.test.mock.mockito.MockBean;

              public class SomeTest {
                  @MockBean(name="someName", answer="Answers.RETURNS_DEFAULTS", classes=String.class, value=String.class)
                  private String someService;
              }
              """,
            // Expected output after applying the recipe
            """
              import org.mockito.Answers;
              import org.springframework.test.context.bean.override.mockito.MockitoBean;

              public class SomeTest {
                  @MockitoBean(name="someName", answers="Answers.RETURNS_DEFAULTS")
                  private String someService;
              }
              """
          )
        );
    }

    @Test
    void replacesMockBeanWithParamsWithMockitoBeanWithParams() {
        rewriteRun(
          // Input source file before applying the recipe
          java(
            """
              import org.springframework.boot.test.mock.mockito.MockBean;

              public class SomeTest {
                  @MockBean(name="bean1")
                  private String someService;
              }
              """,
            // Expected output after applying the recipe
            """
              import org.springframework.test.context.bean.override.mockito.MockitoBean;

              public class SomeTest {
                  @MockitoBean(name="bean1")
                  private String someService;
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeOtherAnnotations() {
        rewriteRun(
          java(
            """
              import org.springframework.boot.test.mock.mockito.MockBean;

              public class SomeTest {

              @MockBean
              @Deprecated
              private String someService;
              }
              """,
            """
              import org.springframework.test.context.bean.override.mockito.MockitoBean;

              public class SomeTest {

              @MockitoBean
              @Deprecated
              private String someService;
              }
              """
          )
        );
    }

    @Test
    void handlesNoMockBeanImport() {
        rewriteRun(
          java(
            """
              public class SomeTest {
                  @org.springframework.boot.test.mock.mockito.MockBean
                  private String someService;
              }
              """,
            """
              public class SomeTest {
                  @org.springframework.test.context.bean.override.mockito.MockitoBean
                  private String someService;
              }
              """
          )
        );
    }

    @Test
    void replacesMockBeanWithMockitoBeanAndSpyBeanWithMockitoSpyBean() {
        rewriteRun(
          // Input source file before applying the recipe
          java(
            """
              import org.springframework.boot.test.mock.mockito.MockBean;
              import org.springframework.boot.test.mock.mockito.SpyBean;

              public class SomeTest {
                  @SpyBean
                  private String someService;

                  @MockBean
                  private String someMockService;
              }
              """,
            // Expected output after applying the recipe
            """
              import org.springframework.test.context.bean.override.mockito.MockitoBean;
              import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

              public class SomeTest {
                  @MockitoSpyBean
                  private String someService;

                  @MockitoBean
                  private String someMockService;
              }
              """
          )
        );
    }

    @Test
    void replacesSpyBeanWithMockitoSpyBean() {
        rewriteRun(
          // Input source file before applying the recipe
          java(
            """
              import org.springframework.boot.test.mock.mockito.SpyBean;

              public class SomeTest {
                  @SpyBean
                  private String someService;
              }
              """,
            // Expected output after applying the recipe
            """
              import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

              public class SomeTest {
                  @MockitoSpyBean
                  private String someService;
              }
              """
          )
        );
    }

    @Test
    void replacesSpyBeanWithMockitoSpyBeanwithAttributes() {
        rewriteRun(
          // Input source file before applying the recipe
          java(
            """
              import org.springframework.boot.test.mock.mockito.SpyBean;

              public class SomeTest {
                  @SpyBean(name="someName", classes=String.class, value=String.class, proxyTargetAware=true)
                  private String someService;
              }
              """,
            // Expected output after applying the recipe
            """
              import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

              public class SomeTest {
                  @MockitoSpyBean(name="someName")
                  private String someService;
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeOtherAnnotationsSpyBean() {
        rewriteRun(
          java(
            """
              import org.springframework.boot.test.mock.mockito.SpyBean;

              public class SomeTest {

              @SpyBean
              @Deprecated
              private String someService;
              }
              """,
            """
              import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

              public class SomeTest {

              @MockitoSpyBean
              @Deprecated
              private String someService;
              }
              """
          )
        );
    }

    @Test
    void handlesNoSpyBeanImport() {
        rewriteRun(
          java(
            """
              public class SomeTest {
                  @org.springframework.boot.test.mock.mockito.SpyBean
                  private String someService;
              }
              """,
            """
              public class SomeTest {
                  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
                  private String someService;
              }
              """
          )
        );
    }

    @Test
    void doesNothingWhenNoAnnotationPresent() {
        rewriteRun(
          java(
            """
              public class SomeTest {
                  private String someService;
              }
              """
          )
        );
    }
}

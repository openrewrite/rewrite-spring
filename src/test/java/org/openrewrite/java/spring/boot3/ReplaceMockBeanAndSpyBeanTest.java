package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceMockBeanAndSpyBeanTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceMockBeanAndSpyBean())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-test"));
    }

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

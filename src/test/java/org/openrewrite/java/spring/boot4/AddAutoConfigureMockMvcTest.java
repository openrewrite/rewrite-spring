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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddAutoConfigureMockMvcTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddAutoConfigureMockMvc())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-test-3",
              "spring-beans-6")
            .dependsOn(
              """
                package org.springframework.test.web.servlet;
                public interface MockMvc {}
                """,
              """
                package org.springframework.boot.webmvc.test.autoconfigure;
                public @interface AutoConfigureMockMvc {}
                """
            ));
    }

    @DocumentExample
    @Test
    void shouldAddAnnotationIfMockMvcIsUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.context.SpringBootTest;
              import org.springframework.test.web.servlet.MockMvc;

              @SpringBootTest
              class ExampleTest {
                  MockMvc mockMvc;
              }
              """,
            """
              import org.springframework.boot.test.context.SpringBootTest;
              import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
              import org.springframework.test.web.servlet.MockMvc;

              @AutoConfigureMockMvc
              @SpringBootTest
              class ExampleTest {
                  MockMvc mockMvc;
              }
              """
          )
        );
    }

    @Test
    void shouldNotAddAnnotationWithoutMockMvc() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.context.SpringBootTest;

              @SpringBootTest
              class ExampleTest {
              }
              """
          )
        );
    }

    @Test
    void shouldNotAddDuplicateAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.context.SpringBootTest;
              import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
              import org.springframework.test.web.servlet.MockMvc;

              @AutoConfigureMockMvc
              @SpringBootTest
              class ExampleTest {
                  MockMvc mockMvc;
              }
              """
          )
        );
    }
}

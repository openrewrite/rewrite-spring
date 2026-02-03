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

class AddAutoConfigureTestRestTemplateTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddAutoConfigureTestRestTemplate())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-resttestclient-4",
              "spring-boot-autoconfigure-3",
              "spring-boot-3",
              "spring-boot-test-3",
              "spring-boot-test-autoconfigure-3",
              "spring-beans-6",
              "spring-context-6",
              "spring-web-6",
              "spring-core-6"));
    }

    @DocumentExample
    @Test
    void shouldAddAnnotationIfTypeIsUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.resttestclient.TestRestTemplate;
              import org.springframework.boot.test.context.SpringBootTest;

              @SpringBootTest
              class ExampleTest {
                  TestRestTemplate testRestTemplate;
              }
              """,
            """
              import org.springframework.boot.resttestclient.TestRestTemplate;
              import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
              import org.springframework.boot.test.context.SpringBootTest;

              @AutoConfigureTestRestTemplate
              @SpringBootTest
              class ExampleTest {
                  TestRestTemplate testRestTemplate;
              }
              """
          )
        );
    }

    @Test
    void shouldNotAddAnnotationIfNoTestRestTemplate() {
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
    void shouldNotAddAnnotationIfNoSpringBootTest() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.resttestclient.TestRestTemplate;

              class ExampleTest {
                  TestRestTemplate testRestTemplate;
              }
              """
          )
        );
    }

    @Test
    void shouldAddAnnotationForOldPackageLocation() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-resttestclient-4",
              "spring-boot-test-3",
              "spring-beans-6")
            .dependsOn(
              "package org.springframework.boot.test.web.client;" +
              "public class TestRestTemplate {}"
            )),
          //language=java
          java(
            """
              import org.springframework.boot.test.web.client.TestRestTemplate;
              import org.springframework.boot.test.context.SpringBootTest;

              @SpringBootTest
              class ExampleTest {
                  TestRestTemplate testRestTemplate;
              }
              """,
            """
              import org.springframework.boot.test.web.client.TestRestTemplate;
              import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
              import org.springframework.boot.test.context.SpringBootTest;

              @AutoConfigureTestRestTemplate
              @SpringBootTest
              class ExampleTest {
                  TestRestTemplate testRestTemplate;
              }
              """
          )
        );
    }
}

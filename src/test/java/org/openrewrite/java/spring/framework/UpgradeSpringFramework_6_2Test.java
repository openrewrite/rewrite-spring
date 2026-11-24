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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class UpgradeSpringFramework_6_2Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.1"))
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.1"));
    }

    @DocumentExample
    @Test
    void migrateFromHttpUrlToFromUriString() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.util.UriComponentsBuilder;

              class A {
                  void test() {
                      String url = "https://example.com";
                      UriComponentsBuilder.fromHttpUrl(url).queryParam("foo", "bar");
                  }
              }
              """,
            """
              import org.springframework.web.util.UriComponentsBuilder;

              class A {
                  void test() {
                      String url = "https://example.com";
                      UriComponentsBuilder.fromUriString(url).queryParam("foo", "bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateFromHttpUrlToFromUriStringStaticImported() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.springframework.web.util.UriComponentsBuilder.fromHttpUrl;

              class A {
                  void test() {
                      String url = "https://example.com";
                      fromHttpUrl(url).queryParam("foo", "bar");
                  }
              }
              """,
            """
              import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

              class A {
                  void test() {
                      String url = "https://example.com";
                      fromUriString(url).queryParam("foo", "bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateFromHttpUrlToFromUriStringInKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.springframework.web.util.UriComponentsBuilder

              class A {
                  fun test() {
                      val url = "https://example.com"
                      UriComponentsBuilder.fromHttpUrl(url).queryParam("foo", "bar")
                  }
              }
              """,
            """
              import org.springframework.web.util.UriComponentsBuilder

              class A {
                  fun test() {
                      val url = "https://example.com"
                      UriComponentsBuilder.fromUriString(url).queryParam("foo", "bar")
                  }
              }
              """
          )
        );
    }
}

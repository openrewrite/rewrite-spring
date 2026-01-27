/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateHttpHeadersMultiValueMapMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new MigrateHttpHeadersMultiValueMapMethods())
          //language=java
          .parser(JavaParser.fromJavaVersion().dependsOn(
            """
              package org.springframework.http;

              import java.util.HashMap;
              import java.util.List;

              public class HttpHeaders extends HashMap<String, List<String>> {
                  public String getFirst(String headerName) {
                      List<String> values = get(headerName);
                      return values != null && !values.isEmpty() ? values.get(0) : null;
                  }
                  public void add(String headerName, String headerValue) {
                  }
              }
              """
          ));
    }

    @DocumentExample
    @Test
    void migrateContainsKeyToContainsHeader() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpHeaders;

              class A {
                  void foo(HttpHeaders headers) {
                      boolean hasAuth = headers.containsKey("Authorization");
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;

              class A {
                  void foo(HttpHeaders headers) {
                      boolean hasAuth = headers.containsHeader("Authorization");
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateKeySetToHeaderNames() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpHeaders;
              import java.util.Set;

              class A {
                  void foo(HttpHeaders headers) {
                      Set<String> names = headers.keySet();
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;
              import java.util.Set;

              class A {
                  void foo(HttpHeaders headers) {
                      Set<String> names = headers.headerNames();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateEntrySetToHeaderSet() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpHeaders;
              import java.util.List;
              import java.util.Map;
              import java.util.Set;

              class A {
                  void foo(HttpHeaders headers) {
                      Set<Map.Entry<String, List<String>>> entries = headers.entrySet();
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;
              import java.util.List;
              import java.util.Map;
              import java.util.Set;

              class A {
                  void foo(HttpHeaders headers) {
                      Set<Map.Entry<String, List<String>>> entries = headers.headerSet();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyNonHttpHeadersMap() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;
              import java.util.HashMap;

              class A {
                  void foo() {
                      Map<String, String> map = new HashMap<>();
                      boolean hasKey = map.containsKey("key");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyHttpHeadersMethodsNotRemoved() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpHeaders;

              class A {
                  void foo(HttpHeaders headers) {
                      headers.add("X-Custom", "value");
                      String first = headers.getFirst("X-Custom");
                      boolean empty = headers.isEmpty();
                      int size = headers.size();
                  }
              }
              """
          )
        );
    }
}

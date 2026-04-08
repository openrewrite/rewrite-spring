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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseObjectUtilsIsEmptyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-core-5.+"))
          .recipe(new UseObjectUtilsIsEmpty());
    }

    @DocumentExample
    @Test
    void replacesForStringArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.StringUtils;

              class A {
                  boolean test(String s) {
                      return StringUtils.isEmpty(s);
                  }
              }
              """,
            """
              import org.springframework.util.ObjectUtils;

              class A {
                  boolean test(String s) {
                      return ObjectUtils.isEmpty(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesForObjectArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.StringUtils;

              class A {
                  boolean test(Object o) {
                      return StringUtils.isEmpty(o);
                  }
              }
              """,
            """
              import org.springframework.util.ObjectUtils;

              class A {
                  boolean test(Object o) {
                      return ObjectUtils.isEmpty(o);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/475")
    @Test
    void doesNotReplaceForListArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.springframework.util.StringUtils;

              class A {
                  boolean test(List<Object> list) {
                      return StringUtils.isEmpty(list);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/475")
    @Test
    void doesNotReplaceForMapArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Map;
              import org.springframework.util.StringUtils;

              class A {
                  boolean test(Map<String, Object> map) {
                      return StringUtils.isEmpty(map);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/475")
    @Test
    void doesNotReplaceForArrayArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.StringUtils;

              class A {
                  boolean test(String[] arr) {
                      return StringUtils.isEmpty(arr);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/475")
    @Test
    void doesNotReplaceForOptionalArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Optional;
              import org.springframework.util.StringUtils;

              class A {
                  boolean test(Optional<String> opt) {
                      return StringUtils.isEmpty(opt);
                  }
              }
              """
          )
        );
    }
}

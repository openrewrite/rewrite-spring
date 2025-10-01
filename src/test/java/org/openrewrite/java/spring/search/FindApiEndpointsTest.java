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
package org.openrewrite.java.spring.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.table.ApiEndpoints;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class FindApiEndpointsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindApiEndpoints())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-5.+", "spring-context-5.+"));
    }

    @DocumentExample
    @Test
    void withinController() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.stereotype.Controller;
              import org.springframework.web.bind.annotation.*;

              @Controller
              class PersonController {
                  @GetMapping("/count")
                  int count() {
                    return 42;
                  }
              }
              """,
            """
              import org.springframework.stereotype.Controller;
              import org.springframework.web.bind.annotation.*;

              @Controller
              class PersonController {
                  /*~~(GET /count)~~>*/@GetMapping("/count")
                  int count() {
                    return 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void webClient() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RequestMapping("/person")
              class PersonController {
                  @GetMapping("/count")
                  int count() {
                    return 42;
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.*;

              @RequestMapping("/person")
              class PersonController {
                  /*~~(GET /person/count)~~>*/@GetMapping("/count")
                  int count() {
                    return 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void withResponseBody() {
        rewriteRun(
          spec -> spec.dataTable(ApiEndpoints.Row.class, rows ->
            assertThat(rows).singleElement()
              .matches(row -> row.getMethodSignature().contains("Person person()"))
              .matches(row -> row.getLeadingAnnotations().contains("@ResponseBody"))),
          //language=java
          java(
            """
              import org.springframework.stereotype.Controller;
              import org.springframework.web.bind.annotation.*;

              @Controller
              class PersonController {
                  @GetMapping("/person")
                  @ResponseBody Person person() {
                      return new Person();
                  }
              }

              class Person {
                  int age = 42;
              }
              """,
            """
              import org.springframework.stereotype.Controller;
              import org.springframework.web.bind.annotation.*;

              @Controller
              class PersonController {
                  /*~~(GET /person)~~>*/@GetMapping("/person")
                  @ResponseBody Person person() {
                      return new Person();
                  }
              }

              class Person {
                  int age = 42;
              }
              """
          )
        );
    }

    @Test
    void multiplePathsOneMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RequestMapping({"/person", "/people"})
              class PersonController {
                  @GetMapping({"/count", "/length"})
                  int count() {
                    return 42;
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.*;

              @RequestMapping({"/person", "/people"})
              class PersonController {
                  /*~~(GET /person/count, /person/length, /people/count, /people/length)~~>*/@GetMapping({"/count", "/length"})
                  int count() {
                    return 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void pathDefinedOnlyOnController() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.stereotype.Controller;
              import org.springframework.web.bind.annotation.*;

              @Controller
              @RequestMapping("/count")
              class PersonController {
                  @GetMapping
                  int count() {
                    return 42;
                  }
              }
              """,
            """
              import org.springframework.stereotype.Controller;
              import org.springframework.web.bind.annotation.*;

              @Controller
              @RequestMapping("/count")
              class PersonController {
                  /*~~(GET /count)~~>*/@GetMapping
                  int count() {
                    return 42;
                  }
              }
              """
          )
        );
    }
}

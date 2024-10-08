/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.search;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Disabled
class FindApiEndpointsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindApiEndpoints())
          .parser(JavaParser.fromJavaVersion().classpath("spring-web", "spring-context"));
    }

    @Test
    @DocumentExample
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
    @DocumentExample
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
}

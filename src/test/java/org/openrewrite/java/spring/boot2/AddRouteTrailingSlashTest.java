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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot3.AddRouteTrailingSlash;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddRouteTrailingSlashTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans", "spring-context", "spring-boot", "spring-security", "spring-web", "spring-core"))
          .recipe(new AddRouteTrailingSlash());
    }

    @DocumentExample
    @Test
    void simpleCase() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class ExampleController {

                  @GetMapping("/get")
                  public String getExample() {
                      return "This is a GET example.";
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class ExampleController {

                  @GetMapping({"/get", "/get/"})
                  public String getExample() {
                      return "This is a GET example.";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfWithTrailingSlash() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class ExampleController {

                  @GetMapping("/get/")
                  public String getExample() {
                      return "This is a GET example.";
                  }

                  @RequestMapping(value = "/request/", method = RequestMethod.GET)
                  public String requestExample() {
                      return "This is a generic request example.";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWithWildcard() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class ExampleController {

                  @GetMapping("/**")
                  public String getExample() {
                      return "This is a GET example.";
                  }
              }
              """
          )
        );
    }

    @Test
    void allSixKindHttpVerbMappings() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping("/example")
              public class ExampleController {

                  @GetMapping("/get")
                  public String getExample() {
                      return "This is a GET example.";
                  }

                  @PostMapping("/post")
                  public String postExample() {
                      return "This is a POST example.";
                  }

                  @PutMapping("/put")
                  public String putExample() {
                      return "This is a PUT example.";
                  }

                  @PatchMapping("/patch")
                  public String patchExample() {
                      return "This is a PATCH example.";
                  }

                  @DeleteMapping("/delete")
                  public String deleteExample() {
                      return "This is a DELETE example.";
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping({"/example", "/example/"})
              public class ExampleController {

                  @GetMapping({"/get", "/get/"})
                  public String getExample() {
                      return "This is a GET example.";
                  }

                  @PostMapping({"/post", "/post/"})
                  public String postExample() {
                      return "This is a POST example.";
                  }

                  @PutMapping({"/put", "/put/"})
                  public String putExample() {
                      return "This is a PUT example.";
                  }

                  @PatchMapping({"/patch", "/patch/"})
                  public String patchExample() {
                      return "This is a PATCH example.";
                  }

                  @DeleteMapping({"/delete", "/delete/"})
                  public String deleteExample() {
                      return "This is a DELETE example.";
                  }
              }
              """
          )
        );
    }

    @Test
    void mappingHasValue() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class ExampleController {
                  @RequestMapping(value = "/request", method = RequestMethod.GET)
                  public String requestExample() {
                      return "This is a generic request example.";
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class ExampleController {
                  @RequestMapping(value = {"/request", "/request/"}, method = RequestMethod.GET)
                  public String requestExample() {
                      return "This is a generic request example.";
                  }
              }
              """
          )
        );
    }
}

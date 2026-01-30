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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({"SimplifiableAnnotation", "MethodMayBeStatic"})
class ImplicitWebAnnotationNamesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ImplicitWebAnnotationNames())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-5.+"))
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "spring-web-5.+"));
    }

    @DocumentExample
    @Test
    void removeUnnecessaryAnnotationArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(@PathVariable("id") Long id,
                                                        @PathVariable(required = false) Long p2,
                                                        @PathVariable(value = "p3") Long anotherName) {
                      System.out.println(anotherName);
                  }
              }
              """,
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(@PathVariable Long id,
                                                        @PathVariable(required = false) Long p2,
                                                        @PathVariable(value = "p3") Long anotherName) {
                      System.out.println(anotherName);
                  }
              }
              """
          )
        );
    }

    @Issue("#4")
    @Test
    void removeUnnecessarySpacingInFollowingAnnotationArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(
                          @RequestParam(name = "count", defaultValue = 3) int count) {
                  }
              }
              """,
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(
                          @RequestParam(defaultValue = 3) int count) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/340")
    @Test
    void autoFormatAfterRemovingArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(@PathVariable("id")Long id) {
                  }
              }
              """,
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(@PathVariable Long id) {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveModelAttributeOnMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.web.bind.annotation.*;
              import java.util.*;

              public class UsersController {
                  @ModelAttribute("types")
                  public Collection<String> populateUserTypes() {
                      return Arrays.asList("free", "premium");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRenamePathVariable() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;

              public class UsersController {
                  public ResponseEntity<String> getUser(@PathVariable("uid") Long id,
                                                        @PathVariable(value = "another_name") Long anotherName) {
                  }
              }
              """
          )
        );
    }

    @Nested
    class Kotlin {
        @Test
        void removeUnnecessaryAnnotationArgument() {
            //language=kotlin
            rewriteRun(
              kotlin(
                """
                  import org.springframework.http.ResponseEntity
                  import org.springframework.web.bind.annotation.*

                  @RestController
                  @RequestMapping("/users")
                  class UsersController {
                      @GetMapping("/{id}")
                      fun getUser(@PathVariable("id") id: Long,
                                  @PathVariable(required = false) p2: Long?,
                                  @PathVariable(value = "p3") anotherName: Long): ResponseEntity<String> {
                          return ResponseEntity.ok(anotherName)
                      }
                  }
                  """,
                """
                  import org.springframework.http.ResponseEntity
                  import org.springframework.web.bind.annotation.*

                  @RestController
                  @RequestMapping("/users")
                  class UsersController {
                      @GetMapping("/{id}")
                      fun getUser(@PathVariable id: Long,
                                  @PathVariable(required = false) p2: Long?,
                                  @PathVariable(value = "p3") anotherName: Long): ResponseEntity<String> {
                          return ResponseEntity.ok(anotherName)
                      }
                  }
                  """
              )
            );
        }

        @ExpectedToFail("Whitespaces in Kotlin around a TypeExpression are problematic see https://github.com/openrewrite/rewrite-kotlin/issues/477. Use a formatter to prevent these situations.")
        @Test
        void annotationNoWhitespaceBetweenAnnotationAndVariable() {
            //language=kotlin
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              kotlin(
                """
                  import org.springframework.http.ResponseEntity
                  import org.springframework.web.bind.annotation.*

                  @RestController
                  @RequestMapping("/users")
                  class UsersController {
                      @GetMapping("/{id}")
                      fun getUser(@PathVariable("id")id: Long): ResponseEntity<String> {
                          return ResponseEntity.ok("")
                      }
                  }
                  """,
                """
                  import org.springframework.http.ResponseEntity
                  import org.springframework.web.bind.annotation.*

                  @RestController
                  @RequestMapping("/users")
                  class UsersController {
                      @GetMapping("/{id}")
                      fun getUser(@PathVariable id: Long): ResponseEntity<String> {
                          return ResponseEntity.ok("")
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doesNotRenamePathVariable() {
            //language=kotlin
            rewriteRun(
              kotlin(
                """
                  import org.springframework.http.ResponseEntity
                  import org.springframework.web.bind.annotation.*

                  class UsersController {
                      fun getUser(@PathVariable("uid") id: Long,
                                  @PathVariable(value = "another_name") anotherName: Long): ResponseEntity<String> {
                          return ResponseEntity.ok("")
                      }
                  }
                  """
              )
            );
        }

        @Test
        void alreadyMigratedNoChange() {
            //language=kotlin
            rewriteRun(
              kotlin(
                """
                  import org.springframework.http.ResponseEntity
                  import org.springframework.web.bind.annotation.*

                  @RestController
                  @RequestMapping("/users")
                  class UsersController {
                      @GetMapping("/{id}")
                      fun getUser(@PathVariable id: Long,
                                  @PathVariable(required = false) p2: Long?): ResponseEntity<String> {
                          return ResponseEntity.ok("")
                      }
                  }
                  """
              )
            );
        }
    }
}

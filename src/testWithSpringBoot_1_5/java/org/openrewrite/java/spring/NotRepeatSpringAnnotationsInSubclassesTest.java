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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NotRepeatSpringAnnotationsInSubclassesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NotRepeatSpringAnnotationsInSubclasses())
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans", "spring-boot",
            "spring-context", "spring-core", "spring-web"));
    }

    @Test
    void removeLeadingAutowiredAnnotation() {
        //language=java
        rewriteRun(
          java(
          """
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RequestBody;

              public interface UserApi {

                  @PostMapping("/users/{id}")
                  String updateUser(
                      @PathVariable("id") Long id,
                      @RequestBody UserData request
                  );

                  class UserData {
                      private String firstName;
                      private String lastName;

                      public String getFirstName() {return firstName;}

                      public void setFirstName(String firstName) {
                          this.firstName = firstName;
                      }

                      public String getLastName() {
                          return lastName;
                      }

                      public void setLastName(String lastName) {
                          this.lastName = lastName;
                      }
                  }
              }
          """),
          java(
            """
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RequestBody;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              public class UserController implements UserApi {

                  @PostMapping("/users/{id}")
                  String updateUser(
                      @PathVariable("id") Long id,
                      @RequestBody UserData request
                  ) {
                      return "User " + id + " updated: " + request.getFirstName() + " " + request.getLastName();
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              public class UserController implements UserApi {

                  @Override
                  public String updateUser(Long id, UserData request) {
                      return "User " + id + " updated: " + request.getFirstName() + " " + request.getLastName();
                  }
              }
              """
          )
        );
    }
}

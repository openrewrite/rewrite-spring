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

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

class UpdateApiManifestTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdateApiManifest())
          .parser(JavaParser.fromJavaVersion().classpath("spring-web"));
    }

    @Test
    void requestMappingWithMethod() {
        rewriteRun(
          spec -> spec.cycles(1).expectedCyclesThatMakeChanges(1),
          //language=java
          java(
            """
              import java.util.List;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;
                            
              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @RequestMapping(value = "/post", method = RequestMethod.POST)
                  public ResponseEntity<List<String>> getUsersPost() {
                      return null;
                  }
              }
              """
          ),
          text(
            null,
            "POST /users/post",
            spec -> spec.path("META-INF/api-manifest.txt")
          )
        );
    }
}

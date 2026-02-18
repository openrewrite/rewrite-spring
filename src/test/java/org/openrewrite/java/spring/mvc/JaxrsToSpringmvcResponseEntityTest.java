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
package org.openrewrite.java.spring.mvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JaxrsToSpringmvcResponseEntityTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JaxrsToSpringmvcResponseEntity())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "jakarta.ws.rs-api-4", "javax.ws.rs-api-2"));
    }

    @DocumentExample
    @Test
    void migrateResponseJavaxTest() {
        rewriteRun(
          java(
            """
              import javax.ws.rs.core.HttpHeaders;
              import javax.ws.rs.core.MediaType;
              import javax.ws.rs.core.Response;
              import javax.ws.rs.core.Response.Status;

              class TestExample {

                  class TestResponse {
                      private String message;

                      TestResponse(String message) {
                          this.message = message;
                      }

                      String getMessage() {
                          return message;
                      }
                  }

                  Response test0() {
                      return Response.ok().build();
                  }

                  Response test1() {
                      return Response.ok("Test Response").build();
                  }

                  Response test2() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.ok().entity(response).build();
                  }

                  Response test3() {
                      return Response.ok().entity("Test Response").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).build();
                  }

                  Response test4() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).entity(response).build();
                  }

                  Response test5() {
                      return Response.status(Response.Status.UNAUTHORIZED).build();
                  }

                  Response test6() {
                      return Response.status(Response.Status.CREATED).entity("Test Response").build();
                  }

                  Response test7() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.status(Status.NOT_FOUND).entity(response).build();
                  }

                  Response test8() {
                      return Response.status(Status.BAD_GATEWAY).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).entity("Test Response").build();
                  }

                  Response test9() {
                      return Response.serverError().entity("Test Response").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).build();
                  }

                  Response test10() {
                      return Response.noContent().build();
                  }

                  Response.ResponseBuilder test11() {
                      return Response.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                  }

              }
              """,
            """
              import javax.ws.rs.core.HttpHeaders;
              import javax.ws.rs.core.MediaType;
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.http.ResponseEntity.BodyBuilder;

              class TestExample {

                  class TestResponse {
                      private String message;

                      TestResponse(String message) {
                          this.message = message;
                      }

                      String getMessage() {
                          return message;
                      }
                  }

                  ResponseEntity test0() {
                      return ResponseEntity.ok().build();
                  }

                  ResponseEntity test1() {
                      return ResponseEntity.ok("Test Response");
                  }

                  ResponseEntity test2() {
                      TestResponse response = new TestResponse("Test Response");
                      return ResponseEntity.ok().body(response);
                  }

                  ResponseEntity test3() {
                      return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                  }

                  ResponseEntity test4() {
                      TestResponse response = new TestResponse("Test Response");
                      return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body(response);
                  }

                  ResponseEntity test5() {
                      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                  }

                  ResponseEntity test6() {
                      return ResponseEntity.status(HttpStatus.CREATED).body("Test Response");
                  }

                  ResponseEntity test7() {
                      TestResponse response = new TestResponse("Test Response");
                      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                  }

                  ResponseEntity test8() {
                      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                  }

                  ResponseEntity test9() {
                      return ResponseEntity.internalServerError().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                  }

                  ResponseEntity test10() {
                      return ResponseEntity.noContent().build();
                  }

                  BodyBuilder test11() {
                      return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                  }

              }
              """
          )
        );
    }

    @Test
    void migrateResponseJakartaTest() {
        rewriteRun(
          java(
            """
              import jakarta.ws.rs.core.HttpHeaders;
              import jakarta.ws.rs.core.MediaType;
              import jakarta.ws.rs.core.Response;
              import jakarta.ws.rs.core.Response.Status;

              class TestExample {

                  class TestResponse {
                      private String message;

                      TestResponse(String message) {
                          this.message = message;
                      }

                      String getMessage() {
                          return message;
                      }
                  }

                  Response test0() {
                      return Response.ok().build();
                  }

                  Response test1() {
                      return Response.ok("Test Response").build();
                  }

                  Response test2() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.ok().entity(response).build();
                  }

                  Response test3() {
                      return Response.ok().entity("Test Response").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).build();
                  }

                  Response test4() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).entity(response).build();
                  }

                  Response test5() {
                      return Response.status(Response.Status.UNAUTHORIZED).build();
                  }

                  Response test6() {
                      return Response.status(Response.Status.CREATED).entity("Test Response").build();
                  }

                  Response test7() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.status(Status.NOT_FOUND).entity(response).build();
                  }

                  Response test8() {
                      return Response.status(Status.BAD_GATEWAY).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).entity("Test Response").build();
                  }

                  Response test9() {
                      return Response.serverError().entity("Test Response").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).build();
                  }

                  Response test10() {
                      return Response.noContent().build();
                  }

                  Response.ResponseBuilder test11() {
                      return Response.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                  }

              }
              """,
            """
              import jakarta.ws.rs.core.HttpHeaders;
              import jakarta.ws.rs.core.MediaType;
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.http.ResponseEntity.BodyBuilder;

              class TestExample {

                  class TestResponse {
                      private String message;

                      TestResponse(String message) {
                          this.message = message;
                      }

                      String getMessage() {
                          return message;
                      }
                  }

                  ResponseEntity test0() {
                      return ResponseEntity.ok().build();
                  }

                  ResponseEntity test1() {
                      return ResponseEntity.ok("Test Response");
                  }

                  ResponseEntity test2() {
                      TestResponse response = new TestResponse("Test Response");
                      return ResponseEntity.ok().body(response);
                  }

                  ResponseEntity test3() {
                      return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                  }

                  ResponseEntity test4() {
                      TestResponse response = new TestResponse("Test Response");
                      return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body(response);
                  }

                  ResponseEntity test5() {
                      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                  }

                  ResponseEntity test6() {
                      return ResponseEntity.status(HttpStatus.CREATED).body("Test Response");
                  }

                  ResponseEntity test7() {
                      TestResponse response = new TestResponse("Test Response");
                      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                  }

                  ResponseEntity test8() {
                      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                  }

                  ResponseEntity test9() {
                      return ResponseEntity.internalServerError().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                  }

                  ResponseEntity test10() {
                      return ResponseEntity.noContent().build();
                  }

                  BodyBuilder test11() {
                      return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                  }

              }
              """
          )
        );
    }

}

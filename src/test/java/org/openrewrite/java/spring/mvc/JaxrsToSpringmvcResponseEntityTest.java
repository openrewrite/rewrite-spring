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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JaxrsToSpringmvcResponseEntityTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JaxrsToSpringmvcResponseEntity())
          .parser(JavaParser.fromJavaVersion()
            .classpath("jakarta.ws.rs-api", "javax.ws.rs-api"));
    }

    @Test
    void migrateResponseJavaxTest() {
        rewriteRun(
          java(
            """
              import javax.ws.rs.core.HttpHeaders;
              import javax.ws.rs.core.MediaType;
              import javax.ws.rs.core.Response;
              import javax.ws.rs.core.Response.Status;

              public class TestExample {

                  class TestResponse {
                      private String message;

                      public TestResponse(String message) {
                          this.message = message;
                      }

                      public String getMessage() {
                          return message;
                      }
                  }

                  public Response test0() {
                      return Response.ok().build();
                  }

                  public Response test1() {
                      return Response.ok("Test Response").build();
                  }

                  public Response test2() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.ok().entity(response).build();
                  }

                  public Response test3() {
                      return Response.ok().entity("Test Response").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).build();
                  }

                  public Response test4() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).entity(response).build();
                  }

                  public Response test5() {
                      return Response.status(Response.Status.UNAUTHORIZED).build();
                  }

                  public Response test6() {
                      return Response.status(Response.Status.CREATED).entity("Test Response").build();
                  }

                  public Response test7() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.status(Status.NOT_FOUND).entity(response).build();
                  }

                  public Response test8() {
                      return Response.status(Status.BAD_GATEWAY).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).entity("Test Response").build();
                  }

                  public Response test9() {
                      return Response.serverError().entity("Test Response").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).build();
                  }

                  public Response test10() {
                      return Response.noContent().build();
                  }

                  public Response.ResponseBuilder test11() {
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

               public class TestExample {

                   class TestResponse {
                       private String message;

                       public TestResponse(String message) {
                           this.message = message;
                       }

                       public String getMessage() {
                           return message;
                       }
                   }

                   public ResponseEntity test0() {
                       return ResponseEntity.ok().build();
                   }

                   public ResponseEntity test1() {
                       return ResponseEntity.ok("Test Response");
                   }

                   public ResponseEntity test2() {
                       TestResponse response = new TestResponse("Test Response");
                       return ResponseEntity.ok().body(response);
                   }

                   public ResponseEntity test3() {
                       return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                   }

                   public ResponseEntity test4() {
                       TestResponse response = new TestResponse("Test Response");
                       return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body(response);
                   }

                   public ResponseEntity test5() {
                       return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                   }

                   public ResponseEntity test6() {
                       return ResponseEntity.status(HttpStatus.CREATED).body("Test Response");
                   }

                   public ResponseEntity test7() {
                       TestResponse response = new TestResponse("Test Response");
                       return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                   }

                   public ResponseEntity test8() {
                       return ResponseEntity.status(HttpStatus.BAD_GATEWAY).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                   }

                   public ResponseEntity test9() {
                       return ResponseEntity.internalServerError().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                   }

                   public ResponseEntity test10() {
                       return ResponseEntity.noContent().build();
                   }

                   public BodyBuilder test11() {
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

              public class TestExample {

                  class TestResponse {
                      private String message;

                      public TestResponse(String message) {
                          this.message = message;
                      }

                      public String getMessage() {
                          return message;
                      }
                  }

                  public Response test0() {
                      return Response.ok().build();
                  }

                  public Response test1() {
                      return Response.ok("Test Response").build();
                  }

                  public Response test2() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.ok().entity(response).build();
                  }

                  public Response test3() {
                      return Response.ok().entity("Test Response").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).build();
                  }

                  public Response test4() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).entity(response).build();
                  }

                  public Response test5() {
                      return Response.status(Response.Status.UNAUTHORIZED).build();
                  }

                  public Response test6() {
                      return Response.status(Response.Status.CREATED).entity("Test Response").build();
                  }

                  public Response test7() {
                      TestResponse response = new TestResponse("Test Response");
                      return Response.status(Status.NOT_FOUND).entity(response).build();
                  }

                  public Response test8() {
                      return Response.status(Status.BAD_GATEWAY).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).entity("Test Response").build();
                  }

                  public Response test9() {
                      return Response.serverError().entity("Test Response").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).build();
                  }

                  public Response test10() {
                      return Response.noContent().build();
                  }

                  public Response.ResponseBuilder test11() {
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

               public class TestExample {

                   class TestResponse {
                       private String message;

                       public TestResponse(String message) {
                           this.message = message;
                       }

                       public String getMessage() {
                           return message;
                       }
                   }

                   public ResponseEntity test0() {
                       return ResponseEntity.ok().build();
                   }

                   public ResponseEntity test1() {
                       return ResponseEntity.ok("Test Response");
                   }

                   public ResponseEntity test2() {
                       TestResponse response = new TestResponse("Test Response");
                       return ResponseEntity.ok().body(response);
                   }

                   public ResponseEntity test3() {
                       return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                   }

                   public ResponseEntity test4() {
                       TestResponse response = new TestResponse("Test Response");
                       return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body(response);
                   }

                   public ResponseEntity test5() {
                       return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                   }

                   public ResponseEntity test6() {
                       return ResponseEntity.status(HttpStatus.CREATED).body("Test Response");
                   }

                   public ResponseEntity test7() {
                       TestResponse response = new TestResponse("Test Response");
                       return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                   }

                   public ResponseEntity test8() {
                       return ResponseEntity.status(HttpStatus.BAD_GATEWAY).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                   }

                   public ResponseEntity test9() {
                       return ResponseEntity.internalServerError().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).body("Test Response");
                   }

                   public ResponseEntity test10() {
                       return ResponseEntity.noContent().build();
                   }

                   public BodyBuilder test11() {
                       return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                   }

               }
              """
          )
        );
    }

}

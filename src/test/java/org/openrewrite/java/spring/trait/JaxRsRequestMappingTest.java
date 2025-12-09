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
package org.openrewrite.java.spring.trait;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class JaxRsRequestMappingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
              @Override
              public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                  J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                  JaxRsRequestMapping.Matcher matcher = new JaxRsRequestMapping.Matcher();
                  for (J.Annotation annotation : service(AnnotationService.class).getAllAnnotations(getCursor())) {
                      m = matcher.get(annotation, getCursor())
                              .map(requestMapping -> SearchResult.found(method,
                                      requestMapping.getHttpMethod() + " " + requestMapping.getPath()))
                              .orElse(m);
                  }
                  return m;
              }
          }))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "jakarta.ws.rs-api-2"));
    }

    @DocumentExample
    @Test
    void javaxGetMapping() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.ws.rs.GET;
              import javax.ws.rs.Path;

              @Path("/person")
              class PersonController {
                  @GET
                  @Path("/count")
                  int count() {
                    return 42;
                  }
              }
              """,
            """
              import javax.ws.rs.GET;
              import javax.ws.rs.Path;

              @Path("/person")
              class PersonController {
                  /*~~(GET /person/count)~~>*/@GET
                  @Path("/count")
                  int count() {
                    return 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void javaxPostMapping() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.ws.rs.POST;
              import javax.ws.rs.Path;

              @Path("/person")
              class PersonController {
                  @POST
                  @Path("/create")
                  void create() {
                  }
              }
              """,
            """
              import javax.ws.rs.POST;
              import javax.ws.rs.Path;

              @Path("/person")
              class PersonController {
                  /*~~(POST /person/create)~~>*/@POST
                  @Path("/create")
                  void create() {
                  }
              }
              """
          )
        );
    }

    @Test
    void javaxDeleteMapping() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.ws.rs.DELETE;
              import javax.ws.rs.Path;

              @Path("/person")
              class PersonController {
                  @DELETE
                  @Path("/{id}")
                  void delete() {
                  }
              }
              """,
            """
              import javax.ws.rs.DELETE;
              import javax.ws.rs.Path;

              @Path("/person")
              class PersonController {
                  /*~~(DELETE /person/{id})~~>*/@DELETE
                  @Path("/{id}")
                  void delete() {
                  }
              }
              """
          )
        );
    }

    @Test
    void pathOnlyOnClass() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.ws.rs.GET;
              import javax.ws.rs.Path;

              @Path("/person")
              class PersonController {
                  @GET
                  int count() {
                    return 42;
                  }
              }
              """,
            """
              import javax.ws.rs.GET;
              import javax.ws.rs.Path;

              @Path("/person")
              class PersonController {
                  /*~~(GET /person)~~>*/@GET
                  int count() {
                    return 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void pathOnlyOnMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.ws.rs.GET;
              import javax.ws.rs.Path;

              class PersonController {
                  @GET
                  @Path("/count")
                  int count() {
                    return 42;
                  }
              }
              """,
            """
              import javax.ws.rs.GET;
              import javax.ws.rs.Path;

              class PersonController {
                  /*~~(GET /count)~~>*/@GET
                  @Path("/count")
                  int count() {
                    return 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void noPathAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.ws.rs.GET;

              class PersonController {
                  @GET
                  int count() {
                    return 42;
                  }
              }
              """,
            """
              import javax.ws.rs.GET;

              class PersonController {
                  /*~~(GET /)~~>*/@GET
                  int count() {
                    return 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void allHttpMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.ws.rs.*;

              @Path("/api")
              class ApiController {
                  @GET
                  @Path("/get")
                  void get() {}

                  @POST
                  @Path("/post")
                  void post() {}

                  @PUT
                  @Path("/put")
                  void put() {}

                  @DELETE
                  @Path("/delete")
                  void delete() {}

                  @PATCH
                  @Path("/patch")
                  void patch() {}

                  @HEAD
                  @Path("/head")
                  void head() {}

                  @OPTIONS
                  @Path("/options")
                  void options() {}
              }
              """,
            """
              import javax.ws.rs.*;

              @Path("/api")
              class ApiController {
                  /*~~(GET /api/get)~~>*/@GET
                  @Path("/get")
                  void get() {}

                  /*~~(POST /api/post)~~>*/@POST
                  @Path("/post")
                  void post() {}

                  /*~~(PUT /api/put)~~>*/@PUT
                  @Path("/put")
                  void put() {}

                  /*~~(DELETE /api/delete)~~>*/@DELETE
                  @Path("/delete")
                  void delete() {}

                  /*~~(PATCH /api/patch)~~>*/@PATCH
                  @Path("/patch")
                  void patch() {}

                  /*~~(HEAD /api/head)~~>*/@HEAD
                  @Path("/head")
                  void head() {}

                  /*~~(OPTIONS /api/options)~~>*/@OPTIONS
                  @Path("/options")
                  void options() {}
              }
              """
          )
        );
    }
}

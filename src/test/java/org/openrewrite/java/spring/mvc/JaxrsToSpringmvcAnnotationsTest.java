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

class JaxrsToSpringmvcAnnotationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JaxrsToSpringmvcAnnotations())
          .parser(JavaParser.fromJavaVersion()
            .classpath("jakarta.ws.rs-api", "javax.ws.rs-api"));
    }

    @Test
    void jaxrsToSpringmvcAnnotationsTest1() {
        rewriteRun(
          java(
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.Path;
              import jakarta.ws.rs.QueryParam;

              @Path(value = "test")
              public class Test {

                  @GET
                  @Path(value = "test")
                  public String test(@QueryParam("p1") String p1,
                          @QueryParam(value = "p2") String p2) {
                      return "test";
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestParam;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              @RequestMapping("test")
              public class Test {

                  @GetMapping("test")
                  public String test(@RequestParam(value = "p1", required = false) String p1,
                          @RequestParam(value = "p2", required = false) String p2) {
                      return "test";
                  }
              }
              """
          )
        );
    }

    @Test
    void jaxrsToSpringmvcAnnotationsTest2() {
        rewriteRun(
          java(
            """
              import javax.ws.rs.FormParam;
              import javax.ws.rs.GET;
              import javax.ws.rs.Path;

              @Path("test")
              public class Test {

                  @GET
                  @Path("test")
                  public String test(@FormParam("p1") String p1,
                          @FormParam(value = "p2") String p2) {
                      return "test";
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestParam;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              @RequestMapping("test")
              public class Test {

                  @GetMapping("test")
                  public String test(@RequestParam(value = "p1", required = false) String p1,
                          @RequestParam(value = "p2", required = false) String p2) {
                      return "test";
                  }
              }
              """
          )
        );
    }

    @Test
    void jaxrsToSpringmvcAnnotationsTest3() {
        rewriteRun(
          java(
            """
              import jakarta.ws.rs.DefaultValue;
              import jakarta.ws.rs.FormParam;
              import jakarta.ws.rs.POST;
              import jakarta.ws.rs.Path;
              import jakarta.ws.rs.Produces;
              import jakarta.ws.rs.QueryParam;

              @Path("test")
              @Produces({"application/json", "text/plain"})
              public class Test {

                  @POST
                  @Produces( "application/json")
                  @Path("test")
                  public String test(@DefaultValue("1") @QueryParam("p1") String p1,
                          @DefaultValue(value = "2") @FormParam(value = "p2") String p2) {
                      return "test";
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestParam;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              @RequestMapping(value = "test", produces = {"application/json", "text/plain"})
              public class Test {

                  @PostMapping(value = "test", produces = "application/json")
                  public String test(@RequestParam(value = "p1", defaultValue = "1") String p1,
                          @RequestParam(value = "p2", defaultValue = "2") String p2) {
                      return "test";
                  }
              }
              """
          )
        );
    }

    @Test
    void jaxrsToSpringmvcAnnotationsTest4() {
        rewriteRun(
          java("""
              import javax.ws.rs.Consumes;
              import javax.ws.rs.PUT;
              import javax.ws.rs.Path;
              import javax.ws.rs.PathParam;

              @Consumes({"application/json"})
              @Path("test")
              public class Test {

                  @PUT
                  @Path(value = "test")
                  @Consumes(value = {"multipart/form-data"})
                  public String test(@PathParam("p1") String p1,
                          @PathParam(value = "p2") String p2) {
                      return "test";
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.PutMapping;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              @RequestMapping(value = "test", consumes = {"application/json"})
              public class Test {

                  @PutMapping(value = "test", consumes = {"multipart/form-data"})
                  public String test(@PathVariable("p1") String p1,
                          @PathVariable("p2") String p2) {
                      return "test";
                  }
              }
              """
          )
        );
    }

    @Test
    void jaxrsToSpringmvcAnnotationsTest5() {
        rewriteRun(
          java("""
              import jakarta.ws.rs.Consumes;
              import jakarta.ws.rs.DELETE;
              import jakarta.ws.rs.HeaderParam;
              import jakarta.ws.rs.Path;
              import jakarta.ws.rs.Produces;

              @Path(value = "test")
              @Consumes(value = {"application/json", "text/plain"})
              @Produces(value = "application/json")
              public class Test {

                  @DELETE
                  @Consumes(value = "application/json")
                  @Produces({"application/json", "text/plain"})
                  @Path("test")
                  public String test(@HeaderParam("p1") String p1,
                          @HeaderParam(value = "p2") String p2) {
                      return "test";
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.DeleteMapping;
              import org.springframework.web.bind.annotation.RequestHeader;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              @RequestMapping(value = "test", produces = "application/json", consumes = {"application/json", "text/plain"})
              public class Test {

                  @DeleteMapping(value = "test", produces = {"application/json", "text/plain"}, consumes = "application/json")
                  public String test(@RequestHeader(value = "p1", required = false) String p1,
                          @RequestHeader(value = "p2", required = false) String p2) {
                      return "test";
                  }
              }
              """
          )
        );
    }

    @Test
    void jaxrsToSpringmvcAnnotationsTest6() {
        rewriteRun(
          java("""
              import javax.ws.rs.DefaultValue;
              import javax.ws.rs.HeaderParam;
              import javax.ws.rs.POST;
              import javax.ws.rs.Consumes;
              import javax.ws.rs.core.Context;
              import javax.ws.rs.core.Request;

              @Consumes(value = {"application/json"})
              public class Test {

                  @POST
                  public String test(@DefaultValue("1") @HeaderParam("p1") String p1,
                          @DefaultValue(value = "2") @HeaderParam(value = "p2") String p2) {
                      return "test";
                  }

                  @POST
                  @Consumes(value = {"multipart/form-data", "text/plain"})
                  public String test1(@Context Request request) {
                      return "test";
                  }
              }
              """,
            """
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RequestHeader;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RestController;

              import javax.ws.rs.core.Request;

              @RestController
              @RequestMapping(consumes = {"application/json"})
              public class Test {

                  @PostMapping
                  public String test(@RequestHeader(value = "p1", defaultValue = "1") String p1,
                          @RequestHeader(value = "p2", defaultValue = "2") String p2) {
                      return "test";
                  }

                  @PostMapping(consumes = {"multipart/form-data", "text/plain"})
                  public String test1(Request request) {
                      return "test";
                  }
              }
              """
          )
        );
    }

    @Test
    void jaxrsToSpringmvcAnnotationsTest7() {
        rewriteRun(
          java(
            """
              import jakarta.ws.rs.Consumes;
              import jakarta.ws.rs.POST;
              import jakarta.ws.rs.PUT;
              import jakarta.ws.rs.Path;
              import jakarta.ws.rs.core.MediaType;

              public class Test1 {

                  class Obj {
                      String name;
                  }

                  @POST
                  @Consumes("multipart/form-data")
                  @Path("/test0")
                  public void test0(Obj myObj) {}

                  @POST
                  @Consumes(value = {"multipart/form-data"})
                  @Path("/test1")
                  public void test1(Obj myObj) {}

                  @PUT
                  @Consumes({"multipart/form-data", "application/json"})
                  @Path("/test2")
                  public void test2(Obj myObj) {}

                  @PUT
                  @Consumes(value = "application/json")
                  @Path("/test3")
                  public void test3(Obj myObj) {
                      String str;
                  }

                  @PUT
                  @Consumes({MediaType.MULTIPART_FORM_DATA})
                  @Path("/test4")
                  public void test4(Obj myObj) {}

              }
              """,
            """
              import jakarta.ws.rs.core.MediaType;
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.PutMapping;
              import org.springframework.web.bind.annotation.RequestBody;
              import org.springframework.web.bind.annotation.RequestPart;

              public class Test1 {

                  class Obj {
                      String name;
                  }

                  @PostMapping(value = "/test0", consumes = "multipart/form-data")
                  public void test0(@RequestPart Obj myObj) {}

                  @PostMapping(value = "/test1", consumes = {"multipart/form-data"})
                  public void test1(@RequestPart Obj myObj) {}

                  @PutMapping(value = "/test2", consumes = {"multipart/form-data", "application/json"})
                  public void test2(@RequestBody Obj myObj) {}

                  @PutMapping(value = "/test3", consumes = "application/json")
                  public void test3(@RequestBody Obj myObj) {
                      String str;
                  }

                  @PutMapping(value = "/test4", consumes = {MediaType.MULTIPART_FORM_DATA})
                  public void test4(@RequestPart Obj myObj) {}

              }
              """
          )
        );
    }
}

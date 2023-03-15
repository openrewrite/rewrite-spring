package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot3.AddRouteTrailingSlash;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class AddRouteTrailingSlashTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("spring-beans", "spring-context", "spring-boot", "spring-security", "spring-web", "spring-core"))
          .recipe(new AddRouteTrailingSlash());
    }

    @Test
    void simpleCase() {
        rewriteRun(
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

                  @GetMapping(value = {"/get", "/get/"})
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
              @RequestMapping(value = {"/example", "/example/"})
              public class ExampleController {

                  @GetMapping(value = {"/get", "/get/"})
                  public String getExample() {
                      return "This is a GET example.";
                  }
                            
                  @PostMapping(value = {"/post", "/post/"})
                  public String postExample() {
                      return "This is a POST example.";
                  }
                            
                  @PutMapping(value = {"/put", "/put/"})
                  public String putExample() {
                      return "This is a PUT example.";
                  }
                            
                  @PatchMapping(value = {"/patch", "/patch/"})
                  public String patchExample() {
                      return "This is a PATCH example.";
                  }
                            
                  @DeleteMapping(value = {"/delete", "/delete/"})
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

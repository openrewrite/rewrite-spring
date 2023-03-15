package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MaintainTrailingSlashURLMappingsTest implements RewriteTest {

    @Test
    void noChangeWithConfigOverriddenByWebMvcConfigurer() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath( "spring-webmvc", "spring-web"))
            .recipe(new MaintainTrailingSlashURLMappings()),
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
              """
          ),
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyMvcConfig implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                      configurer.setUseTrailingSlashMatch(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithConfigOverriddenByWebFluxConfigurer() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("spring-webflux", "spring-web"))
            .recipe(new MaintainTrailingSlashURLMappings()),
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
              """
          ),
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.reactive.config.PathMatchConfigurer;
              import org.springframework.web.reactive.config.WebFluxConfigurer;

              @Configuration
              public class MyWebConfig implements WebFluxConfigurer {
                  @Override
                  public void configurePathMatching(PathMatchConfigurer configurer) {
                      configurer.setUseTrailingSlashMatch(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void noConfigOverridden() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("spring-webmvc", "spring-web"))
            .recipe(new MaintainTrailingSlashURLMappings()),
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

}

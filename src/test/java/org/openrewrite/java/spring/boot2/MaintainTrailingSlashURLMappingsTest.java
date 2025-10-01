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
import org.openrewrite.java.spring.boot3.MaintainTrailingSlashURLMappings;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MaintainTrailingSlashURLMappingsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MaintainTrailingSlashURLMappings())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-webmvc-5.+", "spring-webflux-5.+", "spring-web-5.+", "spring-context-5.+"));
    }

    @DocumentExample
    @Test
    void noConfigOverridden() {
        //language=java
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
    void noChangeWithConfigOverriddenByWebMvcConfigurer() {
        //language=java
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
    void addSetUseTrailingSlashMatchForWebMvcConfigurer() {
        //language=java
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
                  }
              }
              """,
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
        //language=java
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
    void addSetUseTrailingSlashForWebFluxConfigurer() {
        //language=java
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
                  }
              }
              """,
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
}

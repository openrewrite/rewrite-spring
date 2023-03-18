package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot3.AddSetUseTrailingSlashMatch;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class AddSetUseTrailingSlashMatchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("spring-webmvc", "spring-webflux", "spring-web", "spring-context"))
          .recipe(new AddSetUseTrailingSlashMatch());
    }

    @Test
    void noChangeIfExistWithWebMvcConfigurer() {
        rewriteRun(
          java(
            """
              package com.example.demo;

              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
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
    void noChangeIfExistWithWebflux() {
        rewriteRun(
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
    void addConfigurePathMatchMethodForWebMvcConfigurer() {
        rewriteRun(
          java(
            """
              package com.example.demo;

              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
              }
              """,
            """
              package com.example.demo;

              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
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
    void addConfigurePathMatchMethodForWebFluxConfigurer() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.reactive.config.WebFluxConfigurer;

              @Configuration
              public class MyWebConfig implements WebFluxConfigurer {
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

    @Test
    void addSetUseTrailingSlashMatchCallForWebMvcConfigurer() {
        rewriteRun(
          java(
            """
              package com.example.demo;

              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                  }
              }
              """,
            """
              package com.example.demo;

              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
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
    void addSetUseTrailingSlashMatchForWebFluxConfigurer() {
        rewriteRun(
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

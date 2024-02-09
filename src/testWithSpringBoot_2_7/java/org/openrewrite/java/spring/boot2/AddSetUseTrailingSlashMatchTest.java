/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot3.AddSetUseTrailingSlashMatch;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddSetUseTrailingSlashMatchTest implements RewriteTest {

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

    @DocumentExample
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

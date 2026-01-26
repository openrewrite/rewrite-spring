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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddSetUseSuffixPatternMatchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-webmvc", "spring-web-5", "spring-context-5"))
          .recipe(new AddSetUseSuffixPatternMatch());
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
                      configurer.setUseSuffixPatternMatch(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeIfSuffixPatternMatchAlreadySet() {
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
                      configurer.setUseSuffixPatternMatch(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeIfSuffixPatternMatchExplicitlyDisabled() {
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
                      configurer.setUseSuffixPatternMatch(false);
                  }
              }
              """
          )
        );
    }

    @Test
    void addSuffixPatternMatchCallToExistingEmptyMethod() {
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
                      configurer.setUseSuffixPatternMatch(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveExistingConfigurationInMethod() {
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
                      configurer.setUseSuffixPatternMatch(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForNonWebMvcConfigurerClass() {
        rewriteRun(
          java(
            """
              package com.example.demo;

              import org.springframework.context.annotation.Configuration;

              @Configuration
              public class MyConfiguration {
              }
              """
          )
        );
    }

    @Test
    void noChangeForWebFluxConfigurer() {
        // WebFlux does not support suffix pattern matching, so recipe should not apply
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-webflux-5", "spring-web-5", "spring-context-5")),
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.reactive.config.WebFluxConfigurer;

              @Configuration
              public class MyWebConfig implements WebFluxConfigurer {
              }
              """
          )
        );
    }
}

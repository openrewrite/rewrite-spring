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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveDeprecatedPathMappingOptionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.RemoveDeprecatedPathMappingOptions")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-webmvc-5", "spring-webflux-5", "spring-web-5", "spring-context-5"));
    }

    @DocumentExample
    @Test
    void removeSetUseTrailingSlashMatchFromWebMvcConfigurer() {
        rewriteRun(
          //language=java
          java(
            """
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
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeSetUseTrailingSlashMatchFromWebFluxConfigurer() {
        rewriteRun(
          //language=java
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
              """,
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
              """
          )
        );
    }

    @Test
    void removeSetUseSuffixPatternMatch() {
        rewriteRun(
          //language=java
          java(
            """
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
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeSetUseRegisteredSuffixPatternMatch() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                      configurer.setUseRegisteredSuffixPatternMatch(true);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMultipleDeprecatedMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                      configurer.setUseTrailingSlashMatch(true);
                      configurer.setUseSuffixPatternMatch(false);
                      configurer.setUseRegisteredSuffixPatternMatch(false);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFavorPathExtension() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
                      configurer.favorPathExtension(false);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeIgnoreUnknownPathExtensions() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
                      configurer.ignoreUnknownPathExtensions(true);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeSetMatchOptionalTrailingSeparator() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.util.pattern.PathPatternParser;

              class MyConfig {
                  void configure() {
                      PathPatternParser parser = new PathPatternParser();
                      parser.setMatchOptionalTrailingSeparator(true);
                  }
              }
              """,
            """
              import org.springframework.web.util.pattern.PathPatternParser;

              class MyConfig {
                  void configure() {
                      PathPatternParser parser = new PathPatternParser();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveOtherMethodCalls() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
              import org.springframework.web.util.pattern.PathPatternParser;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                      configurer.setPatternParser(new PathPatternParser());
                      configurer.setUseTrailingSlashMatch(true);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
              import org.springframework.web.util.pattern.PathPatternParser;

              @Configuration
              public class MyWebConfiguration implements WebMvcConfigurer {
                  @Override
                  public void configurePathMatch(PathMatchConfigurer configurer) {
                      configurer.setPatternParser(new PathPatternParser());
                  }
              }
              """
          )
        );
    }

}

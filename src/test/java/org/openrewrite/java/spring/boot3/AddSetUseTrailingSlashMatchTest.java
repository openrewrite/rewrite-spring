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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class AddSetUseTrailingSlashMatchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                "spring-webmvc",
                "spring-webflux-5",
                "spring-web-5.3",
                "spring-context-5"))
          .recipe(new AddSetUseTrailingSlashMatch());
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

    @Nested
    class NoChange {
        @Issue("https://github.com/openrewrite/rewrite-spring/issues/1201")
        @Test
        void leaveClassUnchangedWhenMethodTypeIsMissing() {
            InMemoryExecutionContext ctx = new InMemoryExecutionContext();
            //language=java
            J.CompilationUnit cu = (J.CompilationUnit) JavaParser.fromJavaVersion()
              .classpathFromResources(ctx, "spring-webmvc", "spring-web-5.3", "spring-context-5")
              .build()
              .parse(
                """
                  import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
                  import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                  class MyWebConfiguration implements WebMvcConfigurer {
                      @Override
                      public void configurePathMatch(PathMatchConfigurer configurer) {
                      }
                  }
                  """
              )
              .findFirst()
              .orElseThrow();
            J.CompilationUnit withoutMethodType = (J.CompilationUnit) new JavaIsoVisitor<Integer>() {
                @Override
                public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, Integer p) {
                    return method.withMethodType(null);
                }
            }.visitNonNull(cu, 0);

            assertThat(new AddSetUseTrailingSlashMatch().getVisitor().visit(withoutMethodType, ctx)).isSameAs(withoutMethodType);
        }
    }
}

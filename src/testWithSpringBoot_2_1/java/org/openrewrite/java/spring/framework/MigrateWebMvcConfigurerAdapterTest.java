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

class MigrateWebMvcConfigurerAdapterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-webmvc-5", "spring-core-5", "spring-web-5"))
          .recipe(new MigrateWebMvcConfigurerAdapter());
    }

    @DocumentExample
    @Test
    void transformSimple() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

              public class CustomMvcConfigurer extends WebMvcConfigurerAdapter {
                  private final String someArg;
                  public CustomMvcConfigurer(String someArg) {
                      super();
                      this.someArg = someArg;
                  }
              }
              """,
                """
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              public class CustomMvcConfigurer implements WebMvcConfigurer {
                  private final String someArg;
                  public CustomMvcConfigurer(String someArg) {
                      this.someArg = someArg;
                  }
              }
              """
          )
        );
    }

    @Test
    void transformBean() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

              class WebConfig {
                  WebMvcConfigurerAdapter forwardToIndex() {
                      return new WebMvcConfigurerAdapter() {
                      };
                  }
              }
              """,
            """
              import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

              class WebConfig {
                  WebMvcConfigurer forwardToIndex() {
                      return new WebMvcConfigurer() {
                      };
                  }
              }
              """
          )
        );
    }
}

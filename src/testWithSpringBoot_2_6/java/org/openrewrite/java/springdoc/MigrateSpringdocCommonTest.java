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
package org.openrewrite.java.springdoc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateSpringdocCommonTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.springdoc.MigrateSpringdocCommon")
          .parser(JavaParser.fromJavaVersion().classpath(
            "springdoc-openapi-common-1.+",
            "swagger-models-2.+"
          ));
    }

    @Test
    @DocumentExample
    void fixCustomiserAndGroupedOpenApi() {
        // language=java
        rewriteRun(
          java(
            """
              import io.swagger.v3.oas.models.OpenAPI;
              import org.springdoc.core.GroupedOpenApi;
              import org.springdoc.core.customizers.OpenApiCustomiser;

              public class OpenApiConfiguration {

                  public static void groupedOpenApi() {
                      GroupedOpenApi.builder()
                        .group("group")
                        .pathsToMatch("/api/**")
                        .addOpenApiCustomiser(new FoobarOpenApiCustomiser())
                        .build();
                  }

                  public static class FoobarOpenApiCustomiser implements OpenApiCustomiser {
                      @Override
                      public void customise(OpenAPI openApi) {
                      }
                  }
              }
              """, """
              import io.swagger.v3.oas.models.OpenAPI;
              import org.springdoc.core.customizers.OpenApiCustomizer;
              import org.springdoc.core.models.GroupedOpenApi;

              public class OpenApiConfiguration {

                  public static void groupedOpenApi() {
                      GroupedOpenApi.builder()
                        .group("group")
                        .pathsToMatch("/api/**")
                        .addOpenApiCustomizer(new FoobarOpenApiCustomiser())
                        .build();
                  }

                  public static class FoobarOpenApiCustomiser implements OpenApiCustomizer {
                      @Override
                      public void customise(OpenAPI openApi) {
                      }
                  }
              }
              """
          )
        );
    }
}

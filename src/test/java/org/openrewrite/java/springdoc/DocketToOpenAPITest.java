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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class DocketToOpenAPITest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DocketToOpenAPI())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "springfox-core-3.+",
            "springfox-spring-web-3.+",
            "springfox-spi-3.+"
          ));
    }

    @DocumentExample
    @Test
    void docketToOpenAPI() {
        // language=java
        rewriteRun(
          java(
            """
              import springfox.documentation.builders.ApiInfoBuilder;
              import springfox.documentation.service.ApiInfo;
              import springfox.documentation.service.Contact;
              import springfox.documentation.spi.DocumentationType;
              import springfox.documentation.spring.web.plugins.Docket;

              public class SwaggerConfiguration {

                  public Docket api() {
                      return new Docket(DocumentationType.OAS_30)
                          .apiInfo(apiInfo());
                  }

                  private ApiInfo apiInfo() {
                      return new ApiInfoBuilder()
                        .title("My API")
                        .description("API for managing resources")
                        .version("1.0.0")
                        .contact(new Contact("John Doe", "https://example.com", "john@example.com"))
                        .license("Apache 2.0")
                        .licenseUrl("http://www.apache.org/licenses/LICENSE-2.0")
                        .build();
                  }
              }
              """
//            ,
//            """
//              import io.swagger.v3.oas.models.OpenAPI;
//              import springfox.documentation.builders.ApiInfoBuilder;
//              import springfox.documentation.service.ApiInfo;
//              import springfox.documentation.service.Contact;
//
//              public class SwaggerConfiguration {
//
//                  public OpenAPI api() {
//                      return new OpenAPI()
//                        .info(apiInfo());
//                  }
//
//                  private ApiInfo apiInfo() {
//                      return new ApiInfoBuilder()
//                        .title("My API")
//                        .description("API for managing resources")
//                        .version("1.0.0")
//                        .contact(new Contact("John Doe", "https://example.com", "john@example.com"))
//                        .license("Apache 2.0")
//                        .licenseUrl("http://www.apache.org/licenses/LICENSE-2.0")
//                        .build();
//                  }
//              }
//              """
          )
        );
    }

}

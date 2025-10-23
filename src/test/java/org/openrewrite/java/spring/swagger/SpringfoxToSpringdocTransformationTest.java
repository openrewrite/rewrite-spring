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
package org.openrewrite.java.spring.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;

class SpringfoxToSpringdocTransformationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TransformApiInfo())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "springfox-core-3.+",
              "swagger-core-2.+"
            )
          );
    }

    @DocumentExample
    @Test
    void transformApiInfo() {
        rewriteRun(
          mavenProject("project",
            //language=java
            java(
              """
                package com.example;
                import springfox.documentation.service.ApiInfo;
                import springfox.documentation.service.Contact;
                import springfox.documentation.builders.ApiInfoBuilder;
                class Test {
                    ApiInfo apiInfo() {
                      return new ApiInfoBuilder()
                          .title("Springfox petstore API")
                          .description("Lorem Ipsum")
                          .termsOfServiceUrl("http://springfox.io")
                          .contact(new Contact("springfox", "", ""))
                          .license("Apache License Version 2.0")
                          .licenseUrl("https://github.com/springfox/springfox/blob/master/LICENSE")
                          .version("2.0")
                          .build();
                    }
                }
                """,
              """
                package com.example;
                import io.swagger.v3.oas.models.info.Contact;
                import io.swagger.v3.oas.models.info.Info;
                import io.swagger.v3.oas.models.info.License;
                class Test {
                    Info apiInfo() {
                      return new Info()
                          .title("Springfox petstore API")
                          .description("Lorem Ipsum")
                          .termsOfService("http://springfox.io")
                          .contact(new Contact().name("springfox").url("").email(""))
                          .license(new License().name("Apache License Version 2.0").url("https://github.com/springfox/springfox/blob/master/LICENSE"))
                          .version("2.0");
                    }
                }
                """
            )
          )
        );
    }
}

/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.doc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CleanupRemainingSpringfoxTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.springdoc.CleanupRemainingSpringfox")
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package springfox.documentation.service;
                public class ApiInfo {
                    public ApiInfo(String title, String description, String version,
                                   String termsOfServiceUrl, String contact, String license, String licenseUrl) {}
                }
                """,
              """
                package springfox.documentation.spring.web.plugins;
                public class Docket {
                    public Docket(Object type) {}
                    public Docket apiInfo(Object apiInfo) { return this; }
                    public Docket select() { return this; }
                    public Docket build() { return this; }
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void removeOrphanedPrivateMethodsWithSpringfoxTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.service.ApiInfo;

              class SwaggerConfig {

                  public void publicMethod() {
                  }

                  private ApiInfo appInfo() {
                      return new ApiInfo("My API", "Description", "1.0", "", "", "", "");
                  }
              }
              """,
            """
              class SwaggerConfig {

                  public void publicMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenPrivateMethodIsUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.service.ApiInfo;
              import springfox.documentation.spring.web.plugins.Docket;

              class SwaggerConfig {

                  public Docket api() {
                      return new Docket(null).apiInfo(appInfo()).select().build();
                  }

                  private ApiInfo appInfo() {
                      return new ApiInfo("My API", "Description", "1.0", "", "", "", "");
                  }
              }
              """
          )
        );
    }
}

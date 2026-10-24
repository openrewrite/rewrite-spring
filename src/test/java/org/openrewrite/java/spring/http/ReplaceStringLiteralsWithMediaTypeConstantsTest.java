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
package org.openrewrite.java.spring.http;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class ReplaceStringLiteralsWithMediaTypeConstantsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.http.ReplaceStringLiteralsWithMediaTypeConstants")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.2"));
    }

    @DocumentExample
    @Test
    void replaceInModuleWithSpringWeb() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.GetMapping;

                  class GreetingController {
                      @GetMapping(path = "/greeting", produces = "application/json")
                      String greeting() {
                          return "{}";
                      }
                  }
                  """,
                """
                  import org.springframework.http.MediaType;
                  import org.springframework.web.bind.annotation.GetMapping;

                  class GreetingController {
                      @GetMapping(path = "/greeting", produces = MediaType.APPLICATION_JSON_VALUE)
                      String greeting() {
                          return "{}";
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>client</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-web</artifactId>
                            <version>6.2.19</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void noChangeInModuleWithoutSpringWeb() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=java
              java(
                """
                  import java.util.Map;

                  class Client {
                      Map<String, String> headers() {
                          return Map.of("Content-Type", "application/json");
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>client</artifactId>
                    <version>1.0.0</version>
                </project>
                """
            )
          )
        );
    }
}

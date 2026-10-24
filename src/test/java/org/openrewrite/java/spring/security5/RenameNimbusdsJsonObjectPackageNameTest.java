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
package org.openrewrite.java.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class RenameNimbusdsJsonObjectPackageNameTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.security5.RenameNimbusdsJsonObjectPackageName")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "nimbus-jose-jwt-9.13"));
    }

    @DocumentExample
    @Test
    void renamePackage() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import com.nimbusds.jose.shaded.json.JSONObject;
              import com.nimbusds.jose.shaded.json.JSONValue;

              public class TutorialService {
                JSONObject dsl;
                JSONValue value;

                public JSONObject getDsl() {
                  return dsl;
                }

                public JSONValue getValue() {
                  return value;
                }
              }
              """
            ,
            """
              import net.minidev.json.JSONObject;
              import net.minidev.json.JSONValue;

              public class TutorialService {
                JSONObject dsl;
                JSONValue value;

                public JSONObject getDsl() {
                  return dsl;
                }

                public JSONValue getValue() {
                  return value;
                }
              }
              """
          )
        );
    }

    @Test
    void addJsonSmartDependency() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              java(
                """
                  import com.nimbusds.jose.shaded.json.JSONObject;

                  class A {
                    JSONObject dsl;
                  }
                  """,
                """
                  import net.minidev.json.JSONObject;

                  class A {
                    JSONObject dsl;
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.nimbusds</groupId>
                      <artifactId>nimbus-jose-jwt</artifactId>
                      <version>9.13</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>json-smart</artifactId>")
                .actual())
            )
          )
        );
    }
}

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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class PreserveWebjarsLocatorTransitiveDependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot2.PreserveWebjarsLocatorTransitiveDependencies")
          .parser(JavaParser.fromJavaVersion().dependsOn(
            //language=java
            """
              package org.apache.commons.lang3.builder;
              public class ToStringBuilder {
                  public static String reflectionToString(Object o) { return null; }
              }
              """
          ));
    }

    @DocumentExample
    @Test
    void addCommonsLang3WhenUsed() {
        rewriteRun(
          mavenProject("calendar",
            srcMainJava(
              //language=java
              java(
                """
                  import org.apache.commons.lang3.builder.ToStringBuilder;

                  class Event {
                      @Override
                      public String toString() {
                          return ToStringBuilder.reflectionToString(this);
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
                    <artifactId>calendar</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.webjars</groupId>
                            <artifactId>webjars-locator</artifactId>
                            <version>0.50</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>commons-lang3</artifactId>")
                .doesNotContain("<artifactId>jackson-databind</artifactId>")
                .doesNotContain("<artifactId>commons-text</artifactId>")
                .actual())
            )
          )
        );
    }

    @Test
    void noChangeWithoutWebjarsLocator() {
        rewriteRun(
          mavenProject("calendar",
            srcMainJava(
              //language=java
              java(
                """
                  import org.apache.commons.lang3.builder.ToStringBuilder;

                  class Event {
                      @Override
                      public String toString() {
                          return ToStringBuilder.reflectionToString(this);
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
                    <artifactId>calendar</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.webjars</groupId>
                            <artifactId>webjars-locator-core</artifactId>
                            <version>0.50</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void versionlessWhereSpringBootManagesIt() {
        rewriteRun(
          mavenProject("calendar",
            srcMainJava(
              //language=java
              java(
                """
                  import org.apache.commons.lang3.builder.ToStringBuilder;

                  class Event {
                      @Override
                      public String toString() {
                          return ToStringBuilder.reflectionToString(this);
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.18</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>calendar</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.webjars</groupId>
                            <artifactId>webjars-locator</artifactId>
                            <version>0.50</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.18</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>calendar</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-lang3</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.webjars</groupId>
                            <artifactId>webjars-locator</artifactId>
                            <version>0.50</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }
}

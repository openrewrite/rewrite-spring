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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class UsesSpringBootTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromYaml(
          """
            type: specs.openrewrite.org/v1beta/recipe
            name: test.BootOnly
            displayName: Test Boot applicability
            description: Exercise the Boot applicability precondition on resource files.
            preconditions:
              - org.openrewrite.java.spring.boot4.UsesSpringBoot
            recipeList:
              - org.openrewrite.text.FindAndReplace:
                  find: before
                  replace: after
            """,
          "test.BootOnly"
        );
    }

    @Test
    void sourceOnlyInvocationRemainsSupported() {
        rewriteRun(text("before", "after"));
    }

    @Test
    void migrateEveryProjectOfARepositoryWithABootProject() {
        rewriteRun(
          mavenProject("boot",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.7</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>boot-app</artifactId>
                    <version>1</version>
                </project>
                """
            ),
            text("before", "after", source -> source.path("src/main/resources/example.txt"))
          ),
          mavenProject("other",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>other-app</artifactId>
                    <version>1</version>
                </project>
                """
            ),
            text("before", "after", source -> source.path("src/main/resources/example.txt"))
          )
        );
    }

    @Test
    void bootThroughAnotherStarterMigratesModulesWithoutBoot() {
        // As with an internal framework built on Spring Boot: the application gets Boot only transitively,
        // and the library module beside it does not use Boot at all.
        rewriteRun(
          mavenProject("app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-starter</artifactId>
                            <version>4.1.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          ),
          mavenProject("library",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>library</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-context</artifactId>
                            <version>6.2.11</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            text("before", "after", source -> source.path("src/main/resources/example.txt"))
          )
        );
    }

    @Test
    void leaveRepositoryWithoutBootUnchanged() {
        rewriteRun(
          mavenProject("micronaut",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>micronaut-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.micronaut</groupId>
                            <artifactId>micronaut-runtime</artifactId>
                            <version>2.4.2</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            text("before", source -> source.path("src/main/resources/example.txt"))
          ),
          mavenProject("library",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>library</artifactId>
                    <version>1</version>
                </project>
                """
            ),
            text("before", source -> source.path("src/main/resources/example.txt"))
          )
        );
    }
}

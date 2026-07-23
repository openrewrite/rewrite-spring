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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringDataMongoDb_5_0RepresentationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.data.UpgradeSpringDataMongoDb_5_0")
          .parser(JavaParser.fromJavaVersion().dependsOn(
            """
              package org.springframework.data.mongodb.core.mapping;
              public @interface Document {}
              """
          ));
    }

    @Test
    void runsRepresentationDiagnosticThroughComposite() {
        rewriteRun(
          mavenProject("app",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.data</groupId>
                            <artifactId>spring-data-mongodb</artifactId>
                            <version>4.5.13</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .containsPattern("<artifactId>spring-data-mongodb</artifactId>\\s*<version>5\\.0\\.\\d+</version>")
                .actual())
            ),
            java(
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private UUID externalId;
                }
                """,
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(Spring Data MongoDB 5 requires an explicit UUID representation; configure `spring.mongodb.representation.uuid` or `MongoClientSettings.Builder.uuidRepresentation(...)`.)~~>*/private UUID externalId;
                }
                """
            )
          )
        );
    }
}

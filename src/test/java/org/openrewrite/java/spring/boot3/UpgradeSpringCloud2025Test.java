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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

class UpgradeSpringCloud2025Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.cloud2025.UpgradeSpringCloud_2025");
    }

    @DocumentExample
    @Test
    void renameProperty() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>fooservice</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-gateway-webflux</artifactId>
                            <version>4.2.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .containsPattern("4\\.3\\.\\d+")
                .actual())
            ),
            srcMainResources(
              //language=properties
              properties(
                "spring.cloud.gateway.proxy=foo",
                "spring.cloud.gateway.proxy-exchange.webflux=foo",
                spec -> spec.path("application.properties")
              )
            )
          )
        );
    }
}

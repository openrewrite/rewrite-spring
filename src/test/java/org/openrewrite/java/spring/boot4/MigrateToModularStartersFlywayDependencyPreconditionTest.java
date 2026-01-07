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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateToModularStartersFlywayDependencyPreconditionTest implements RewriteTest {

    // TODO: see MigrateToFlyway10Test's

    private static SourceSpecs PARENT_POM = pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>sample-module</module>
                  </modules>
                </project>
                """
    );

    private static final SourceSpecs CLASS_FILE = srcMainJava(
      java(
        """
          class AnyClass {
              String s = "";
          }
          """
      )
    );

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource(
          "/META-INF/rewrite/spring-boot-40-modular-starters-flyway-dependency-precondition.yml",
          "org.openrewrite.java.spring.boot4.MigrateToModularStartersFlywayDependencyPrecondition"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"flyway-database-postgresql", "flyway-mysql"})
    void addFlywayStarterWhenDependencyPresent(String artifactId) {
        rewriteRun(
          mavenProject("sample",
            PARENT_POM,
            mavenProject("sample-module",
              CLASS_FILE,
              pomXml(
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.sample</groupId>
                        <artifactId>sample</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>sample-module</artifactId>
                    <dependencies>
                      <dependency>
                        <groupId>org.flywaydb</groupId>
                        <artifactId>%s</artifactId>
                        <version>10.0.0</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """.formatted(artifactId),
                spec -> spec.after(pom -> assertThat(pom)
                  .contains("<groupId>org.flywaydb</groupId>")
                  .contains("<artifactId>%s</artifactId>".formatted(artifactId))
                  .contains("<artifactId>spring-boot-starter-flyway</artifactId>")
                  .containsPattern("<version>4\\.0\\.\\d+</version>")
                  .actual())
              )
            )
          )
        );
    }

}

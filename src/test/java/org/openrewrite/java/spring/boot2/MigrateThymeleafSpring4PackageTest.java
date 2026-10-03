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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateThymeleafSpring4PackageTest implements RewriteTest {

    @Test
    void migrateDirectSpring4Dependency() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0"),
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0</version>
                  <properties>
                      <thymeleaf.release>3.0.15.RELEASE</thymeleaf.release>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>org.thymeleaf</groupId>
                          <artifactId>thymeleaf-spring4</artifactId>
                          <version>${thymeleaf.release}</version>
                      </dependency>
                      <dependency>
                          <groupId>org.thymeleaf</groupId>
                          <artifactId>thymeleaf</artifactId>
                          <version>${thymeleaf.release}</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(actual -> assertThat(actual)
              .containsPattern("<artifactId>thymeleaf-spring5</artifactId>\\s*<version>(?:\\$\\{thymeleaf.release}|3\\.0\\.15\\.RELEASE)</version>")
              .doesNotContain("<artifactId>thymeleaf-spring4</artifactId>")
              .contains("<thymeleaf.release>3.0.15.RELEASE</thymeleaf.release>")
              .containsPattern("<artifactId>thymeleaf</artifactId>\\s*<version>\\$\\{thymeleaf.release}</version>")
              .actual())
          )
        );
    }

    @Test
    void leaveExistingNewerSpring5Dependency() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0"),
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.thymeleaf</groupId>
                          <artifactId>thymeleaf-spring5</artifactId>
                          <version>3.1.3.RELEASE</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @ParameterizedTest
    @CsvSource({"2, 2_0, 5", "3, 3_0, 6"})
    void migrateSpring4PackagesThroughBootUpgrade(int boot, String version, int thymeleafSpring) {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot" + boot + ".UpgradeSpringBoot_" + version)
            .parser(JavaParser.fromJavaVersion().dependsOn(
              "package org.thymeleaf.spring4; public class SpringTemplateEngine {}",
              "package org.thymeleaf.spring4.templateresolver; public class SpringResourceTemplateResolver {}"
            )),
          java(
            """
              import org.thymeleaf.spring4.SpringTemplateEngine;
              import org.thymeleaf.spring4.templateresolver.SpringResourceTemplateResolver;

              class MailService {
                  SpringTemplateEngine engine;
                  SpringResourceTemplateResolver resolver;
              }
              """,
            """
              import org.thymeleaf.spring%d.SpringTemplateEngine;
              import org.thymeleaf.spring%d.templateresolver.SpringResourceTemplateResolver;

              class MailService {
                  SpringTemplateEngine engine;
                  SpringResourceTemplateResolver resolver;
              }
              """.formatted(thymeleafSpring, thymeleafSpring)
          )
        );
    }
}

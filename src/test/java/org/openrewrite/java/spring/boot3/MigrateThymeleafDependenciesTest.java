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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateThymeleafDependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot3.MigrateThymeleafDependencies");
    }

    @DocumentExample
    @Issue("https://github.com/moderneinc/customer-requests/issues/2588")
    @Test
    void migrateThymeleafSpring5ToSpring6WithResolvableVersion() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>org.thymeleaf</groupId>
                    <artifactId>thymeleaf-spring5</artifactId>
                    <version>3.0.15.RELEASE</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("3\\.1\\.\\d+\\.RELEASE").matcher(pom);
                assertThat(version.find()).describedAs("Expected a resolvable 3.1.x version in %s", pom).isTrue();
                //language=xml
                return """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.thymeleaf</groupId>
                        <artifactId>thymeleaf-spring6</artifactId>
                        <version>%s</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """.formatted(version.group());
            })
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/2588")
    @Test
    void migrateThymeleafExtrasSpringSecurity5ToSpring6WithResolvableVersion() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>org.thymeleaf.extras</groupId>
                    <artifactId>thymeleaf-extras-springsecurity5</artifactId>
                    <version>3.0.5.RELEASE</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher version = Pattern.compile("3\\.1\\.\\d+\\.RELEASE").matcher(pom);
                assertThat(version.find()).describedAs("Expected a resolvable 3.1.x version in %s", pom).isTrue();
                //language=xml
                return """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.thymeleaf.extras</groupId>
                        <artifactId>thymeleaf-extras-springsecurity6</artifactId>
                        <version>%s</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """.formatted(version.group());
            })
          )
        );
    }
}

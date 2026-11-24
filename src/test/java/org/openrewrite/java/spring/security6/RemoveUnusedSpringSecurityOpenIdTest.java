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
package org.openrewrite.java.spring.security6;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveUnusedSpringSecurityOpenIdTest implements RewriteTest {

    //language=xml
    private static final String POM = """
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>app</artifactId>
          <version>1.0.0</version>
          <dependencies>
              <dependency>
                  <groupId>org.springframework.security</groupId>
                  <artifactId>spring-security-core</artifactId>
                  <version>5.8.16</version>
              </dependency>
              <dependency>
                  <groupId>org.springframework.security</groupId>
                  <artifactId>spring-security-openid</artifactId>
                  <version>5.8.16</version>
              </dependency>
          </dependencies>
      </project>
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_0");
    }

    @Test
    void removeUnusedOpenId() {
        rewriteRun(
          mavenProject("app",
            pomXml(POM, spec -> spec.after(actual -> {
                assertThat(actual)
                  .doesNotContain("spring-security-openid")
                  .containsPattern(Pattern.compile("<artifactId>spring-security-core</artifactId>\\s*<version>6\\.0\\.\\d+</version>"));
                return actual;
            }))
          )
        );
    }

    @Test
    void keepOpenIdInUse() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
            //language=java
            """
              package org.springframework.security.openid;

              public class OpenIDAuthenticationToken {
              }
              """
          )),
          mavenProject("app",
            pomXml(POM, spec -> spec.after(actual -> {
                assertThat(actual).contains("spring-security-openid");
                return actual;
            })),
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.security.openid.OpenIDAuthenticationToken;

                  class OpenIdLogin {
                      OpenIDAuthenticationToken token;
                  }
                  """
              )
            )
          )
        );
    }
}

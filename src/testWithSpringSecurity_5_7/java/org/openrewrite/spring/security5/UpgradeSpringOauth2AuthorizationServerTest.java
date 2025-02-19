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
package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringOauth2AuthorizationServerTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.security5.UpgradeSpringSecurity_5_7");
    }

    @Test
    @DocumentExample
    void verifyDependencyUpgrades() {
        rewriteRun(
          // language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.security</groupId>
                          <artifactId>spring-security-core</artifactId>
                          <version>5.6.0</version>
                      </dependency>
                      <dependency>
                          <groupId>org.springframework.security</groupId>
                          <artifactId>spring-security-oauth2-authorization-server</artifactId>
                          <version>0.2.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.security</groupId>
                          <artifactId>spring-security-core</artifactId>
                          <version>5.7.14</version>
                      </dependency>
                      <dependency>
                          <groupId>org.springframework.security</groupId>
                          <artifactId>spring-security-oauth2-authorization-server</artifactId>
                          <version>0.3.1</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }
}

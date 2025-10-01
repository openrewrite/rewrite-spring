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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.spring.boot3.DowngradeServletApiWhenUsingJetty;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class DowngradeServletApiWhenUsingJettyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DowngradeServletApiWhenUsingJetty());
    }

    @DocumentExample
    @Test
    void downgradeApiWhenUsingJetty() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <name>demo</name>
                <description>Demo project for Spring Boot</description>
                <properties>
                  <java.version>17</java.version>
                </properties>
                <dependencies>
                  <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-jetty</artifactId>
                    <version>3.0.1</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <name>demo</name>
                <description>Demo project for Spring Boot</description>
                <properties>
                  <jakarta-servlet.version>5.0.0</jakarta-servlet.version>
                  <java.version>17</java.version>
                </properties>
                <dependencies>
                  <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-jetty</artifactId>
                    <version>3.0.1</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void changePropertyIfPresent() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <name>demo</name>
                <description>Demo project for Spring Boot</description>
                <properties>
                  <jakarta-servlet.version>6.0.0</jakarta-servlet.version>
                  <java.version>17</java.version>
                </properties>
                <dependencies>
                  <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-jetty</artifactId>
                    <version>3.0.1</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <name>demo</name>
                <description>Demo project for Spring Boot</description>
                <properties>
                  <jakarta-servlet.version>5.0.0</jakarta-servlet.version>
                  <java.version>17</java.version>
                </properties>
                <dependencies>
                  <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-jetty</artifactId>
                    <version>3.0.1</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void doNotDowngradeIfJettyNotPresent() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <name>demo</name>
                <description>Demo project for Spring Boot</description>
                <properties>
                  <java.version>17</java.version>
                </properties>
              </project>
              """
          )
        );
    }
}

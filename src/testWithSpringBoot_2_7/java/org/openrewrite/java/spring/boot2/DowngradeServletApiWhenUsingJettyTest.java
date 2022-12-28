package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

public class DowngradeServletApiWhenUsingJettyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("org.openrewrite.java.spring.boot3")
                        .build()
                        .activateRecipes("org.openrewrite.java.spring.boot3.DowngradeServletApiWhenUsingJetty")
                );
    }

    @Test
    void downgradeApiWhenUsingJetty() {
        rewriteRun(
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

package org.openrewrite.java.spring.cloud2022;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

public class MigrateProjectTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.spring.cloud2022.MigrateCloudSleuthToMicrometerTracing"))
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "spring-cloud-sleuth-api"));
    }

    @Test
    void migrateSleuthStarter() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-starter-sleuth</artifactId>
                            <version>3.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                                
                    <dependencies>
                        <dependency>
                            <groupId>io.micrometer</groupId>
                            <artifactId>micrometer-tracing-bridge-brave</artifactId>
                            <version>1.0.4</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void migrateOldSleuthCore() {
        rewriteRun(
          mavenProject("project",
            //language=java
            srcMainJava(
              java(
              """
                import org.springframework.cloud.sleuth.annotation.NewSpan;

                class A {
                    @NewSpan
                    void m() {
                    }
                }
                """,
                """
                  import io.micrometer.tracing.annotation.NewSpan;
  
                  class A {
                      @NewSpan
                      void m() {
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-sleuth-core</artifactId>
                            <version>2.2.8.RELEASE</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                                
                    <dependencies>
                        <dependency>
                            <groupId>io.micrometer</groupId>
                            <artifactId>micrometer-tracing</artifactId>
                            <version>1.0.4</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-actuator</artifactId>
                            <version>3.0.5</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-aop</artifactId>
                            <version>3.0.5</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }
}

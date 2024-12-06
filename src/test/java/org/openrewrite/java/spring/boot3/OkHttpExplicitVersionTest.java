/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class OkHttpExplicitVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.boot3.OkHttpExplicitVersion")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "okhttp"));
    }

    @DocumentExample
    @Test
    void addOkHttpDependencyIfUsed() {
        rewriteRun(
          mavenProject("example-project",
            srcMainJava(
              java(
                """
                  import okhttp3.Request;

                  public class Test {
                      public static void main(String[] args) {
                          Request request = new Request.Builder()
                                    .url("url")
                                    .build();
                      }
                  }
                  """
              )
            ),
            pomXml(
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example-project</artifactId>
                    <version>1.0-SNAPSHOT</version>
                </project>
                """,
              """
                  <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example</groupId>
                      <artifactId>example-project</artifactId>
                      <version>1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>com.squareup.okhttp3</groupId>
                              <artifactId>okhttp-bom</artifactId>
                              <version>4.12.0</version>
                              <classifier>import</classifier>
                              <type>pom</type>
                              <scope>runtime</scope>
                          </dependency>
                      </dependencies>
                  </project>
                """
            )
          )
        );
    }

    @Test
    void doNothingifUnused() {
        rewriteRun(
          mavenProject("example-project",
            srcMainJava(
              java(
                """
                  public class Test {
                      public static void main(String[] args) {

                      }
                  }
                  """
              )
            ),
            pomXml(
              """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>example-project</artifactId>
                    <version>1.0-SNAPSHOT</version>
                </project>
                """
            )
          )
        );
    }
}

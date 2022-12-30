/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot2.search;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

/**
 * @author Alex Boyko
 */
class LoggingShutdownHooksTest implements RewriteTest {

    @Language("java")
    private final String app = """
          import org.springframework.boot.autoconfigure.SpringBootApplication;
          
          @SpringBootApplication
          class Application {}
      """;

    //language=java
    private final SourceSpecs found = srcMainJava(
      java(app, """
          import org.springframework.boot.autoconfigure.SpringBootApplication;
          
          /*~~>*/@SpringBootApplication
          class Application {}
      """)
    );

    private final SourceSpecs notFound = srcMainJava(
      java(app)
    );


    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LoggingShutdownHooks())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot"));
    }

    @Test
    void noExplicitPackagingType() {
        rewriteRun(
          found,
          //language=xml
          pomXml(
            """
              <project>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>2.4.13</version>
                      <relativePath/> <!-- lookup parent from repository -->
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>acme</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void explicitJarPackagingType() {
        rewriteRun(
          found,
          //language=xml
          pomXml(
            """
              <project>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>2.4.13</version>
                      <relativePath/> <!-- lookup parent from repository -->
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>acme</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <packaging>jar</packaging>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void explicitWarPackagingType() {
        rewriteRun(
          notFound,
          //language=xml
          pomXml(
            """
              <project>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>2.4.13</version>
                      <relativePath/> <!-- lookup parent from repository -->
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>acme</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <packaging>war</packaging>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void lowerBootProject() {
        rewriteRun(
          notFound,
          //language=xml
          pomXml(
            """
              <project>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>2.3.12.RELEASE</version>
                      <relativePath/> <!-- lookup parent from repository -->
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>acme</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <packaging>war</packaging>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void higherBootProject() {
        rewriteRun(
          notFound,
          //language=xml
          pomXml(
            """
              <project>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>2.5.7</version>
                      <relativePath/> <!-- lookup parent from repository -->
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>acme</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <packaging>war</packaging>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }
}

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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class Boot2UpgradeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-validator-6.0.23.Final"));
    }

    @Test
    void addJavaxValidationApi() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.springframework.samples</groupId>
                  <artifactId>spring-petclinic</artifactId>
                  <version>1.5.22.RELEASE</version>

                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>1.5.22.RELEASE</version>
                  </parent>
                  <name>petclinic</name>

                  <properties>
                    <java.version>1.8</java.version>
                  </properties>

                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-validation</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.springframework.samples</groupId>
                  <artifactId>spring-petclinic</artifactId>
                  <version>1.5.22.RELEASE</version>

                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.0.9.RELEASE</version>
                  </parent>
                  <name>petclinic</name>

                  <properties>
                    <java.version>1.8</java.version>
                  </properties>

                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-validation</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            //language=java
            srcMainJava(
              java(
                """
                  package org.springframework.samples.petclinic.vet;

                  import org.hibernate.validator.constraints.NotEmpty;

                  public class Vet {
                      @NotEmpty
                      private String lastName;
                  }
                  """,
                """
                  package org.springframework.samples.petclinic.vet;

                  import javax.validation.constraints.NotEmpty;

                  public class Vet {
                      @NotEmpty
                      private String lastName;
                  }
                  """
              )
            )
          )
        );
    }
}

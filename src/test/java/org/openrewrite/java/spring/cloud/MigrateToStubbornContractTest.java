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
package org.openrewrite.java.spring.cloud;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.Assertions;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

class MigrateToStubbornContractTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.cloud.MigrateToStubbornContract")
          .parser(JavaParser.fromJavaVersion().dependsOn("""
            package org.springframework.cloud.contract.stubrunner.spring;
            public @interface AutoConfigureStubRunner {
            }
            """));
    }

    @DocumentExample
    @Test
    void migratesMavenCoordinatesAndPlugin() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.cloud</groupId>
                              <artifactId>spring-cloud-contract-dependencies</artifactId>
                              <version>5.0.0</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.cloud</groupId>
                          <artifactId>spring-cloud-contract-verifier</artifactId>
                          <version>5.0.0</version>
                      </dependency>
                      <dependency>
                          <groupId>org.springframework.cloud</groupId>
                          <artifactId>spring-cloud-contract-converters</artifactId>
                          <version>5.0.0</version>
                      </dependency>
                  </dependencies>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.springframework.cloud</groupId>
                              <artifactId>spring-cloud-contract-maven-plugin</artifactId>
                              <version>5.0.0</version>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>sh.stubborn</groupId>
                              <artifactId>stubborn-contract-dependencies</artifactId>
                              <version>5.0.0</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>sh.stubborn</groupId>
                          <artifactId>stubborn-contract-verifier</artifactId>
                          <version>5.0.0</version>
                      </dependency>
                      <dependency>
                          <groupId>sh.stubborn</groupId>
                          <artifactId>stubborn-contract-converters</artifactId>
                          <version>5.0.0</version>
                      </dependency>
                  </dependencies>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>sh.stubborn</groupId>
                              <artifactId>stubborn-contract-maven-plugin</artifactId>
                              <version>5.0.0</version>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void migratesJavaImports() {
        rewriteRun(
          Assertions.java(
            """
              import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;

              @AutoConfigureStubRunner
              class ContractTest {
              }
              """,
            """
              import sh.stubborn.contract.stubrunner.spring.AutoConfigureStubRunner;

              @AutoConfigureStubRunner
              class ContractTest {
              }
              """
          )
        );
    }

    @Test
    void migratesStubRunnerProperties() {
        rewriteRun(
          properties(
            """
              spring.cloud.contract.stubrunner.ids=com.example:demo:+:stubs
              spring.cloud.contract.stubrunner.stubs-mode=remote
              """,
            """
              stubborn.contract.stubrunner.ids=com.example:demo:+:stubs
              stubborn.contract.stubrunner.stubs-mode=remote
              """
          )
        );
    }
}

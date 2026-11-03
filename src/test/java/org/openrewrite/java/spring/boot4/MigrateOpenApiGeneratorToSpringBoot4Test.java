/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class MigrateOpenApiGeneratorToSpringBoot4Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateOpenApiGeneratorToSpringBoot4());
    }

    @DocumentExample
    @Test
    void replaceUseSpringBoot3() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>spring</generatorName>
                                          <configOptions>
                                              <useSpringBoot3>true</useSpringBoot3>
                                          </configOptions>
                                      </configuration>
                                  </execution>
                              </executions>
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
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>spring</generatorName>
                                          <configOptions>
                                              <useSpringBoot4>true</useSpringBoot4>
                                              <useJackson3>true</useJackson3>
                                          </configOptions>
                                      </configuration>
                                  </execution>
                              </executions>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void preserveOtherConfigOptionsAndReplaceInPlace() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>spring</generatorName>
                                          <configOptions>
                                              <interfaceOnly>true</interfaceOnly>
                                              <useSpringBoot3>true</useSpringBoot3>
                                              <useTags>true</useTags>
                                          </configOptions>
                                      </configuration>
                                  </execution>
                              </executions>
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
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>spring</generatorName>
                                          <configOptions>
                                              <interfaceOnly>true</interfaceOnly>
                                              <useTags>true</useTags>
                                              <useSpringBoot4>true</useSpringBoot4>
                                              <useJackson3>true</useJackson3>
                                          </configOptions>
                                      </configuration>
                                  </execution>
                              </executions>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void addOptionsWhenSpringBoot3Absent() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>spring</generatorName>
                                          <configOptions>
                                              <interfaceOnly>true</interfaceOnly>
                                          </configOptions>
                                      </configuration>
                                  </execution>
                              </executions>
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
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>spring</generatorName>
                                          <configOptions>
                                              <interfaceOnly>true</interfaceOnly>
                                              <useSpringBoot4>true</useSpringBoot4>
                                              <useJackson3>true</useJackson3>
                                          </configOptions>
                                      </configuration>
                                  </execution>
                              </executions>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void addConfigOptionsWhenAbsent() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>spring</generatorName>
                                      </configuration>
                                  </execution>
                              </executions>
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
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>spring</generatorName>
                                          <configOptions>
                                              <useSpringBoot4>true</useSpringBoot4>
                                              <useJackson3>true</useJackson3>
                                          </configOptions>
                                      </configuration>
                                  </execution>
                              </executions>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void doNotChangeNonSpringGenerator() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>java</generatorName>
                                          <configOptions>
                                              <useSpringBoot3>true</useSpringBoot3>
                                          </configOptions>
                                      </configuration>
                                  </execution>
                              </executions>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void doNotChangeWhenAlreadyMigrated() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.openapitools</groupId>
                              <artifactId>openapi-generator-maven-plugin</artifactId>
                              <version>7.16.0</version>
                              <executions>
                                  <execution>
                                      <configuration>
                                          <generatorName>spring</generatorName>
                                          <configOptions>
                                              <useSpringBoot4>true</useSpringBoot4>
                                              <useJackson3>true</useJackson3>
                                          </configOptions>
                                      </configuration>
                                  </execution>
                              </executions>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }
}

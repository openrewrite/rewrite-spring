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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class MigrateJsonschema2PojoToSpringBoot4Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJsonschema2PojoToSpringBoot4());
    }

    @DocumentExample
    @Test
    void migrateJacksonAndJakartaValidation() {
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
                              <groupId>org.jsonschema2pojo</groupId>
                              <artifactId>jsonschema2pojo-maven-plugin</artifactId>
                              <version>1.3.0</version>
                              <configuration>
                                  <annotationStyle>jackson2</annotationStyle>
                                  <includeJsr303Annotations>true</includeJsr303Annotations>
                              </configuration>
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
                              <groupId>org.jsonschema2pojo</groupId>
                              <artifactId>jsonschema2pojo-maven-plugin</artifactId>
                              <version>1.3.0</version>
                              <configuration>
                                  <annotationStyle>jackson3</annotationStyle>
                                  <includeJsr303Annotations>true</includeJsr303Annotations>
                                  <useJakartaValidation>true</useJakartaValidation>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void addDefaultJacksonStyle() {
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
                              <groupId>org.jsonschema2pojo</groupId>
                              <artifactId>jsonschema2pojo-maven-plugin</artifactId>
                              <version>1.3.0</version>
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
                              <groupId>org.jsonschema2pojo</groupId>
                              <artifactId>jsonschema2pojo-maven-plugin</artifactId>
                              <version>1.3.0</version>
                              <configuration>
                                  <annotationStyle>jackson3</annotationStyle>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void preserveNonJacksonStyleAndInactiveValidation() {
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
                              <groupId>org.jsonschema2pojo</groupId>
                              <artifactId>jsonschema2pojo-maven-plugin</artifactId>
                              <version>1.3.0</version>
                              <configuration>
                                  <annotationStyle>gson</annotationStyle>
                                  <includeJsr303Annotations>false</includeJsr303Annotations>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void doNotChangeAlreadyMigratedConfiguration() {
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
                              <groupId>org.jsonschema2pojo</groupId>
                              <artifactId>jsonschema2pojo-maven-plugin</artifactId>
                              <version>1.3.0</version>
                              <configuration>
                                  <annotationStyle>jackson3</annotationStyle>
                                  <includeJsr303Annotations>true</includeJsr303Annotations>
                                  <useJakartaValidation>true</useJakartaValidation>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }
}

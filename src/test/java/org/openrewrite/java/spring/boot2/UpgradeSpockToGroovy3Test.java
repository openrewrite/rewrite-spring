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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

@Issue("https://github.com/openrewrite/rewrite-spring/issues/145")
class UpgradeSpockToGroovy3Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpockToGroovy3");
    }

    @Nested
    class Maven {
        @DocumentExample
        @Test
        void upgradeSpockWhenUsingGroovy3() {
            rewriteRun(
              //language=xml
              pomXml(
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>3.0.9</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.3-groovy-2.5</version>
                        <scope>test</scope>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-spring</artifactId>
                        <version>1.3-groovy-2.5</version>
                        <scope>test</scope>
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
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>3.0.9</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>2.0-groovy-3.0</version>
                        <scope>test</scope>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-spring</artifactId>
                        <version>2.0-groovy-3.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void doesNotChangeWhenUsingGroovy25() {
            rewriteRun(
              //language=xml
              pomXml(
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>2.5.14</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>1.3-groovy-2.5</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void doesNotDowngradeWhenAlreadyOnNewerSpock() {
            rewriteRun(
              //language=xml
              pomXml(
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>3.0.9</version>
                      </dependency>
                      <dependency>
                        <groupId>org.spockframework</groupId>
                        <artifactId>spock-core</artifactId>
                        <version>2.1-groovy-3.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }
    }

    @Nested
    class Gradle {
        @DocumentExample
        @Test
        void upgradeSpockWhenUsingGroovy3() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              //language=groovy
              buildGradle(
                """
                  plugins {
                    id 'groovy'
                  }

                  repositories {
                    mavenCentral()
                  }

                  dependencies {
                    implementation 'org.codehaus.groovy:groovy:3.0.9'
                    testImplementation 'org.spockframework:spock-core:1.3-groovy-2.5'
                    testImplementation 'org.spockframework:spock-spring:1.3-groovy-2.5'
                  }

                  tasks.withType(Test).configureEach {
                    useJUnitPlatform()
                  }
                  """,
                """
                  plugins {
                    id 'groovy'
                  }

                  repositories {
                    mavenCentral()
                  }

                  dependencies {
                    implementation 'org.codehaus.groovy:groovy:3.0.9'
                    testImplementation 'org.spockframework:spock-core:2.0-groovy-3.0'
                    testImplementation 'org.spockframework:spock-spring:2.0-groovy-3.0'
                  }

                  tasks.withType(Test).configureEach {
                    useJUnitPlatform()
                  }
                  """
              )
            );
        }
    }
}

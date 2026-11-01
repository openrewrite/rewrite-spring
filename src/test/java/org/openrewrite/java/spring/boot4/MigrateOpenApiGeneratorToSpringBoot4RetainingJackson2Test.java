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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class MigrateOpenApiGeneratorToSpringBoot4RetainingJackson2Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateOpenApiGeneratorToSpringBoot4RetainingJackson2());
    }

    @Test
    void retainJackson2() {
        rewriteRun(pomXml(
          """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>example</groupId>
                <artifactId>demo</artifactId>
                <version>1</version>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.openapitools</groupId>
                            <artifactId>openapi-generator-maven-plugin</artifactId>
                            <version>7.16.0</version>
                            <configuration>
                                <generatorName>spring</generatorName>
                                <configOptions>
                                    <useSpringBoot3>true</useSpringBoot3>
                                </configOptions>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """,
          """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>example</groupId>
                <artifactId>demo</artifactId>
                <version>1</version>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.openapitools</groupId>
                            <artifactId>openapi-generator-maven-plugin</artifactId>
                            <version>7.16.0</version>
                            <configuration>
                                <generatorName>spring</generatorName>
                                <configOptions>
                                    <useSpringBoot4>true</useSpringBoot4>
                                    <useJackson3>false</useJackson3>
                                </configOptions>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """));
    }

    @Test
    void alreadyMigrated() {
        rewriteRun(pomXml(
          """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>example</groupId>
                <artifactId>demo</artifactId>
                <version>1</version>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.openapitools</groupId>
                            <artifactId>openapi-generator-maven-plugin</artifactId>
                            <version>7.16.0</version>
                            <configuration>
                                <generatorName>spring</generatorName>
                                <configOptions>
                                    <useSpringBoot4>true</useSpringBoot4>
                                    <useJackson3>false</useJackson3>
                                </configOptions>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """));
    }
}

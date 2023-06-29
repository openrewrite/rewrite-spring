/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.cloud2022;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class AddLoggingPatternLevelForSleuthTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddLoggingPatternLevelForSleuth());
    }

    @Test
    void addLoggingPatternWhenUsingSleuth() {
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
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-starter-sleuth</artifactId>
                            <version>3.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """),
            //language=properties
            properties(
              "",
              """
                                
                                
                logging.pattern.level="%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
                """,
              s -> s.path("src/main/resources/application.properties")
            ),
            //language=yaml
            yaml(
              "",
              """
                logging:
                  pattern:
                    # Logging pattern containing traceId and spanId; no longer provided through Sleuth by default
                    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
                """,
              s -> s.path("src/main/resources/application.yml")
            )
          )
        );
    }

    @Test
    void doNotAddWhenNotUsingSleuth() {
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
                    <dependencies>
                        <dependency>
                            <groupId>io.micrometer</groupId>
                            <artifactId>micrometer-tracing-bridge-brave</artifactId>
                            <version>1.1.1</version>
                        </dependency>
                    </dependencies>
                </project>
                """),
            properties("", s -> s.path("src/main/resources/application.properties")),
            yaml("", s -> s.path("src/main/resources/application.yml"))
          )
        );
    }
}
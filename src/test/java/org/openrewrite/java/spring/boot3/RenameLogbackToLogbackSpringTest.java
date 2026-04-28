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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.xml.Assertions.xml;

class RenameLogbackToLogbackSpringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RenameLogbackToLogbackSpring());
    }

    @DocumentExample
    @Test
    void renameWhenSpringProfilePresent() {
        rewriteRun(
          xml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <configuration>
                <springProfile name="dev">
                  <logger name="com.example" level="DEBUG"/>
                </springProfile>
                <root level="INFO">
                  <appender-ref ref="STDOUT"/>
                </root>
              </configuration>
              """,
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <configuration>
                <springProfile name="dev">
                  <logger name="com.example" level="DEBUG"/>
                </springProfile>
                <root level="INFO">
                  <appender-ref ref="STDOUT"/>
                </root>
              </configuration>
              """,
            spec -> spec.path("src/main/resources/logback.xml")
                        .afterRecipe(doc -> assertThat(doc.getSourcePath()).hasToString("src/main/resources/logback-spring.xml"))
          )
        );
    }

    @Test
    void renameWhenSpringPropertyPresent() {
        rewriteRun(
          xml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <configuration>
                <springProperty scope="context" name="appName" source="spring.application.name"/>
                <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                  <encoder>
                    <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
                  </encoder>
                </appender>
              </configuration>
              """,
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <configuration>
                <springProperty scope="context" name="appName" source="spring.application.name"/>
                <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                  <encoder>
                    <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
                  </encoder>
                </appender>
              </configuration>
              """,
            spec -> spec.path("src/main/resources/logback.xml")
                        .afterRecipe(doc -> assertThat(doc.getSourcePath()).hasToString("src/main/resources/logback-spring.xml"))
          )
        );
    }

    @Test
    void noChangeWithoutSpringTags() {
        rewriteRun(
          xml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <configuration>
                <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                  <encoder>
                    <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
                  </encoder>
                </appender>
                <root level="INFO">
                  <appender-ref ref="STDOUT"/>
                </root>
              </configuration>
              """,
            spec -> spec.path("src/main/resources/logback.xml")
          )
        );
    }

    @Test
    void noChangeForLogbackTestXml() {
        rewriteRun(
          xml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <configuration>
                <springProfile name="test">
                  <logger name="com.example" level="DEBUG"/>
                </springProfile>
              </configuration>
              """,
            spec -> spec.path("src/test/resources/logback-test.xml")
          )
        );
    }

    @Test
    void deeplyNestedSpringTags() {
        rewriteRun(
          xml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <configuration>
                <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                  <springProfile name="production">
                    <file>/var/log/app.log</file>
                  </springProfile>
                </appender>
              </configuration>
              """,
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <configuration>
                <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                  <springProfile name="production">
                    <file>/var/log/app.log</file>
                  </springProfile>
                </appender>
              </configuration>
              """,
            spec -> spec.path("src/main/resources/logback.xml")
                        .afterRecipe(doc -> assertThat(doc.getSourcePath()).hasToString("src/main/resources/logback-spring.xml"))
          )
        );
    }
}

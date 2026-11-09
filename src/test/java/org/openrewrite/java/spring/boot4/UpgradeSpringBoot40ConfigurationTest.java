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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class UpgradeSpringBoot40ConfigurationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot4.SpringBootProperties_4_0");
    }

    @DocumentExample
    @Test
    void updateHibernateNaminImplicitStrategy() {
        rewriteRun(
          mavenProject("test",
            srcMainResources(
              //language=properties
              properties(
                """
                  # application.properties
                  spring.jpa.hibernate.naming.implicit-strategy=org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy
                  """,
                """
                  # application.properties
                  spring.jpa.hibernate.naming.implicit-strategy=org.springframework.boot.hibernate.SpringImplicitNamingStrategy
                  """
              ),
              //language=yaml
              yaml(
                """
                  ---
                  spring.jpa.hibernate.naming.implicit-strategy: org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy
                  """,
                """
                  ---
                  spring.jpa.hibernate.naming.implicit-strategy: org.springframework.boot.hibernate.SpringImplicitNamingStrategy
                  """
              )
            )
          )
        );
    }

    @Test
    void migrateJacksonDateTimeProperties() {
        rewriteRun(
          mavenProject("test",
            srcMainResources(
              //language=properties
              properties(
                """
                  spring.jackson.serialization.write-dates-as-timestamps=true
                  spring.jackson.serialization.write-date-keys-as-timestamps=false
                  spring.jackson.serialization.write-date-timestamps-as-nanoseconds=true
                  spring.jackson.deserialization.adjust-dates-to-context-time-zone=false
                  spring.jackson.deserialization.read-date-timestamps-as-nanoseconds=true
                  """,
                """
                  spring.jackson.datatype.datetime.write-dates-as-timestamps=true
                  spring.jackson.datatype.datetime.write-date-keys-as-timestamps=false
                  spring.jackson.datatype.datetime.write-date-timestamps-as-nanoseconds=true
                  spring.jackson.datatype.datetime.adjust-dates-to-context-time-zone=false
                  spring.jackson.datatype.datetime.read-date-timestamps-as-nanoseconds=true
                  """
              ),
              //language=yaml
              yaml(
                """
                  spring:
                    jackson:
                      serialization:
                        write-dates-as-timestamps: true
                      deserialization:
                        adjust-dates-to-context-time-zone: false
                  """,
                """
                  spring:
                    jackson:
                      datatype:
                        datetime:
                          write-dates-as-timestamps: true
                          adjust-dates-to-context-time-zone: false
                  """
              )
            )
          )
        );
    }

    @Test
    void migrateJacksonEnumProperties() {
        rewriteRun(
          mavenProject("test",
            srcMainResources(
              //language=properties
              properties(
                """
                  spring.jackson.serialization.write-enums-using-to-string=true
                  spring.jackson.serialization.write-enums-using-index=false
                  spring.jackson.deserialization.read-enums-using-to-string=true
                  spring.jackson.deserialization.fail-on-numbers-for-enums=true
                  """,
                """
                  spring.jackson.datatype.enum.write-enums-using-to-string=true
                  spring.jackson.datatype.enum.write-enums-using-index=false
                  spring.jackson.datatype.enum.read-enums-using-to-string=true
                  spring.jackson.datatype.enum.fail-on-numbers-for-enums=true
                  """
              )
            )
          )
        );
    }

    @Test
    void commentsOutJackson2PropertiesOnlyInModulesWithoutSpringBootJackson2() {
        rewriteRun(
          mavenProject("api",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>api</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-jackson2</artifactId>
                            <version>4.0.6</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainResources(
              //language=properties
              properties(
                """
                  spring.jackson2.date-format=yyyy-MM-dd
                  management.health.mongo.enabled=false
                  """,
                """
                  spring.jackson2.date-format=yyyy-MM-dd
                  management.health.mongodb.enabled=false
                  """,
                spec -> spec.path("application.properties")
              )
            )
          ),
          mavenProject("worker",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>worker</artifactId>
                    <version>1.0-SNAPSHOT</version>
                </project>
                """
            ),
            srcMainResources(
              //language=properties
              properties(
                """
                  spring.jackson2.date-format=yyyy-MM-dd
                  """,
                """
                  # This property is deprecated: Deprecated in favor of Jackson 3
                  # spring.jackson2.date-format=yyyy-MM-dd
                  """,
                spec -> spec.path("application.properties")
              )
            )
          )
        );
    }
}

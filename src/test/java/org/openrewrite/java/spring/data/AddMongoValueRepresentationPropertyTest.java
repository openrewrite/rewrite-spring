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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.spring.data.search.MongoValueRepresentationTestSupport;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

/**
 * Verifies the configuration-editing half: adding explicit properties that preserve the Spring Data
 * MongoDB 4 defaults and flagging existing invalid values without overwriting them.
 */
class AddMongoValueRepresentationPropertyTest extends MongoValueRepresentationTestSupport {

    @Override
    public void defaults(RecipeSpec spec) {
        super.defaults(spec);
        spec.recipe(new AddMongoValueRepresentationProperty());
    }

    @DocumentExample
    @Test
    void generatesBaselinePropertiesFileWhenNoneExists() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              null,
              """
                spring.data.mongodb.representation.big-decimal=string
                spring.mongodb.representation.uuid=java-legacy
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void placesDefaultPreservingPropertiesInExistingMainPropertiesFile() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.application.name=example
                """,
              """
                spring.application.name=example
                spring.data.mongodb.representation.big-decimal=string
                spring.mongodb.representation.uuid=java-legacy
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void placesDefaultPreservingPropertiesInExistingMainYamlFile() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            yaml(
              """
                spring:
                  application:
                    name: example
                """,
              """
                spring:
                  application:
                    name: example
                  mongodb:
                    representation:
                      uuid: java-legacy
                  data:
                    mongodb:
                      representation:
                        big-decimal: string
                """,
              spec -> spec.path("src/main/resources/application.yml")
            )
          )
        );
    }

    @Test
    void prefersPropertiesFileOverYamlWhenBothArePresent() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.application.name=example
                """,
              """
                spring.application.name=example
                spring.data.mongodb.representation.big-decimal=string
                spring.mongodb.representation.uuid=java-legacy
                """,
              spec -> spec.path("src/main/resources/application.properties")
            ),
            yaml(
              """
                spring:
                  application:
                    name: example
                """,
              spec -> spec.path("src/main/resources/application.yml")
            )
          )
        );
    }

    @Test
    void onlyProfileSpecificConfigurationFileGeneratesABaseFileInstead() {
        // application-prod.properties only loads when "prod" is active, so a value placed there
        // wouldn't protect any other profile — a new base application.properties is generated instead,
        // and the profile file is left untouched.
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              """
                spring.application.name=example
                """,
              spec -> spec.path("src/main/resources/application-prod.properties")
            ),
            properties(
              null,
              """
                spring.data.mongodb.representation.big-decimal=string
                spring.mongodb.representation.uuid=java-legacy
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void validJavaConfigurationMakesNoChanges() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            java(
              """
                package com.example;

                import com.mongodb.MongoClientSettings;
                import org.bson.UuidRepresentation;
                import org.springframework.data.mongodb.core.convert.MongoCustomConversions.BigDecimalRepresentation;
                import org.springframework.data.mongodb.core.convert.MongoCustomConversions.MongoConverterConfigurationAdapter;

                class MongoConfiguration {
                    void configure(MongoClientSettings.Builder builder,
                                   MongoConverterConfigurationAdapter adapter) {
                        builder.uuidRepresentation(UuidRepresentation.STANDARD);
                        adapter.bigDecimal(BigDecimalRepresentation.DECIMAL128);
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void validPropertiesConfigurationMakesNoChanges() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.mongodb.representation.uuid=java-legacy
                spring.data.mongodb.representation.big-decimal=string
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void validYamlConfigurationMakesNoChanges() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            yaml(
              """
                spring:
                  mongodb:
                    representation:
                      uuid: c-sharp-legacy
                  data:
                    mongodb:
                      representation:
                        big-decimal: decimal128
                """,
              spec -> spec.path("src/main/resources/application.yml")
            )
          )
        );
    }

    @Test
    void propertyPlaceholdersMakeNoChanges() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.mongodb.representation.uuid=${MONGO_UUID_REPRESENTATION}
                spring.data.mongodb.representation.big-decimal=${MONGO_BIG_DECIMAL_REPRESENTATION}
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void malformedPlaceholderIsMarkedInvalid() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.mongodb.representation.uuid=${MONGO_UUID_REPRESENTATION
                spring.data.mongodb.representation.big-decimal=${MONGO_BIG_DECIMAL_REPRESENTATION
                """,
              """
                # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                spring.mongodb.representation.uuid=${MONGO_UUID_REPRESENTATION
                # `spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.
                spring.data.mongodb.representation.big-decimal=${MONGO_BIG_DECIMAL_REPRESENTATION
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void invalidProfileOverrideIsMarkedEvenWhenDefaultIsValid() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.mongodb.representation.uuid=standard
                spring.data.mongodb.representation.big-decimal=decimal128
                """,
              spec -> spec.path("src/main/resources/application.properties")
            ),
            properties(
              """
                spring.mongodb.representation.uuid=unsupported
                """,
              """
                # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                spring.mongodb.representation.uuid=unsupported
                """,
              spec -> spec.path("src/main/resources/application-test.properties")
            )
          )
        );
    }

    @Test
    void unspecifiedConfigurationMarksExistingProperties() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.mongodb.representation.uuid=unspecified
                spring.data.mongodb.representation.big-decimal=UNSPECIFIED
                """,
              """
                # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                spring.mongodb.representation.uuid=unspecified
                # `spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.
                spring.data.mongodb.representation.big-decimal=UNSPECIFIED
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void unsupportedAndBlankConfigurationMarksExistingProperties() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.mongodb.representation.uuid=unsupported
                spring.data.mongodb.representation.big-decimal=
                """,
              """
                # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                spring.mongodb.representation.uuid=unsupported
                # `spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.
                spring.data.mongodb.representation.big-decimal=
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }
}

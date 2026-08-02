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
package org.openrewrite.java.spring.data.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.spring.table.MongoValueRepresentationFields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class FindMissingMongoValueRepresentationTest extends MongoValueRepresentationTestSupport {

    @DocumentExample
    @Test
    void reportsOncePerProjectAndListsAllAffectedFields() {
        rewriteRun(
          spec -> spec.cycles(4).expectedCyclesThatMakeChanges(3)
            .dataTable(MongoValueRepresentationFields.Row.class, rows ->
              assertThat(rows)
                .extracting(
                  MongoValueRepresentationFields.Row::getOwningType,
                  MongoValueRepresentationFields.Row::getField,
                  MongoValueRepresentationFields.Row::getValueType,
                  MongoValueRepresentationFields.Row::getConfigurationProperty)
                .containsExactlyInAnyOrder(
                  tuple("com.example.Account", "externalId", "UUID", "spring.mongodb.representation.uuid"),
                  tuple("com.example.Account", "balance", "BigDecimal/BigInteger", "spring.data.mongodb.representation.big-decimal"),
                  tuple("com.example.Account", "sequence", "BigDecimal/BigInteger", "spring.data.mongodb.representation.big-decimal"))),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.math.BigDecimal;
                import java.math.BigInteger;
                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private UUID externalId;
                    private BigDecimal balance;
                    private BigInteger sequence;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              null,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("# `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.")
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .contains("# `spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.")
                  .contains("spring.data.mongodb.representation.big-decimal=UNSPECIFIED")
                  .actual())
            )
          )
        );
    }

    @Test
    void placesCommentedDiagnosticsInMainPropertiesFile() {
        rewriteRun(
          spec -> spec
            .dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.application.name=example
                """,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("# `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.")
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .contains("# `spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.")
                  .contains("spring.data.mongodb.representation.big-decimal=UNSPECIFIED")
                  .doesNotContain("~~(")
                  .actual())
            )
          )
        );
    }

    @Test
    void placesCommentedDiagnosticsInMainYamlFile() {
        rewriteRun(
          spec -> spec
            .dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
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
                      # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                      uuid: UNSPECIFIED
                  data:
                    mongodb:
                      representation:
                        # `spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.
                        big-decimal: UNSPECIFIED
                """,
              spec -> spec.path("src/main/resources/application.yml")
            )
          )
        );
    }

    @Test
    void prefersPropertiesFileOverYamlWhenBothArePresent() {
        rewriteRun(
          spec -> spec
            .dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.application.name=example
                """,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .contains("spring.data.mongodb.representation.big-decimal=UNSPECIFIED")
                  .actual())
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
    void onlyProfileSpecificConfigurationFileStillReceivesSuggestions() {
        rewriteRun(
          spec -> spec
            .dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                spring.application.name=example
                """,
              spec -> spec
                .path("src/main/resources/application-prod.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .contains("spring.data.mongodb.representation.big-decimal=UNSPECIFIED")
                  .actual())
            )
          )
        );
    }

    @Test
    void javaConfigurationSuppressesDiagnostics() {
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
    void propertiesConfigurationSuppressesDiagnostics() {
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
    void yamlConfigurationSuppressesDiagnostics() {
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
    void propertyPlaceholdersSuppressDiagnostics() {
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
    void malformedPlaceholderDoesNotSuppressDiagnostics() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
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
              spec -> spec
                .path("src/main/resources/application.properties")
                .afterRecipe(file -> {
                    assertPropertyMarked(file, "spring.mongodb.representation.uuid");
                    assertPropertyMarked(file, "spring.data.mongodb.representation.big-decimal");
                })
            )
          )
        );
    }

    @Test
    void invalidProfileOverrideIsMarkedEvenWhenDefaultIsValid() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows ->
            assertThat(rows)
              .singleElement()
              .extracting(MongoValueRepresentationFields.Row::getField)
              .isEqualTo("externalId")),
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
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
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
              spec -> spec
                .path("src/main/resources/application.properties")
                .afterRecipe(file -> {
                    assertPropertyMarked(file, "spring.mongodb.representation.uuid");
                    assertPropertyMarked(file, "spring.data.mongodb.representation.big-decimal");
                })
            )
          )
        );
    }

    @Test
    void unsupportedAndBlankConfigurationMarksExistingProperties() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
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
              spec -> spec
                .path("src/main/resources/application.properties")
                .afterRecipe(file -> {
                    assertPropertyMarked(file, "spring.mongodb.representation.uuid");
                    assertPropertyMarked(file, "spring.data.mongodb.representation.big-decimal");
                })
            )
          )
        );
    }
}

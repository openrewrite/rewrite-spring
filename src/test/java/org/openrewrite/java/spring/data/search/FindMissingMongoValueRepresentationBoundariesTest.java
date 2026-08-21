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
import org.openrewrite.java.spring.table.MongoValueRepresentationFields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class FindMissingMongoValueRepresentationBoundariesTest extends MongoValueRepresentationTestSupport {

    @Test
    void ignoresNonMongoAndTransientFields() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.math.BigDecimal;
                import java.util.UUID;
                import org.springframework.data.annotation.Persistent;
                import org.springframework.data.annotation.Transient;
                import org.springframework.data.mongodb.core.mapping.Document;

                class NotPersistent {
                    private UUID externalId;
                    private BigDecimal balance;
                }

                @Persistent
                class OtherDataStoreEntity {
                    private UUID externalId;
                    private BigDecimal balance;
                }

                @Document
                class Account {
                    private static UUID staticId;
                    private transient BigDecimal transientBalance;
                    @Transient
                    private UUID ignoredId;

                    void calculate() {
                        BigDecimal local = BigDecimal.ZERO;
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void ignoresExplicitFieldRepresentationAndBigIntegerIds() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.math.BigDecimal;
                import java.math.BigInteger;
                import org.springframework.data.annotation.Id;
                import org.springframework.data.mongodb.core.mapping.Document;
                import org.springframework.data.mongodb.core.mapping.Field;
                import org.springframework.data.mongodb.core.mapping.FieldType;

                @Document
                class Account {
                    @Field(targetType = FieldType.DECIMAL128)
                    private BigDecimal balance;

                    @Id
                    private BigInteger identifier;

                    private BigInteger id;
                }
                """
            )
          )
        );
    }

    @Test
    void fieldLevelMongoAnnotationsQualifyWithoutDocumentAnnotation() {
        rewriteRun(
          spec -> spec.cycles(4).expectedCyclesThatMakeChanges(3)
            .dataTable(MongoValueRepresentationFields.Row.class, rows ->
              assertThat(rows)
                .extracting(MongoValueRepresentationFields.Row::getField)
                .containsExactlyInAnyOrder("mongoId", "reference", "linked")),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.DBRef;
                import org.springframework.data.mongodb.core.mapping.DocumentReference;
                import org.springframework.data.mongodb.core.mapping.MongoId;

                class Account {
                    @MongoId
                    private UUID mongoId;

                    @DocumentReference
                    private UUID reference;

                    @DBRef
                    private UUID linked;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              null,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .actual())
            )
          )
        );
    }

    @Test
    void mongoIdAnnotatedBigIntegerIsTreatedAsAnId() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.math.BigInteger;
                import org.springframework.data.mongodb.core.mapping.Document;
                import org.springframework.data.mongodb.core.mapping.MongoId;

                @Document
                class Account {
                    @MongoId
                    private BigInteger identifier;
                }
                """
            )
          )
        );
    }

    @Test
    void reportsNestedValuesButNotMapKeys() {
        rewriteRun(
          spec -> spec.cycles(4).expectedCyclesThatMakeChanges(3)
            .dataTable(MongoValueRepresentationFields.Row.class, rows ->
              assertThat(rows)
                .extracting(MongoValueRepresentationFields.Row::getField)
                .containsExactlyInAnyOrder("externalIds", "balances")),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.math.BigDecimal;
                import java.util.List;
                import java.util.Map;
                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private List<UUID> externalIds;
                    private Map<UUID, String> labelsByExternalId;
                    private Map<String, BigDecimal> balances;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              null,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .contains("spring.data.mongodb.representation.big-decimal=UNSPECIFIED")
                  .actual())
            )
          )
        );
    }

    @Test
    void testJavaConfigurationDoesNotSuppressMainDiagnostics() {
        rewriteRun(
          spec -> spec.cycles(4).expectedCyclesThatMakeChanges(3),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private UUID externalId;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            java(
              """
                package com.example;

                import com.mongodb.MongoClientSettings;
                import org.bson.UuidRepresentation;

                class TestMongoConfiguration {
                    void configure(MongoClientSettings.Builder builder) {
                        builder.uuidRepresentation(UuidRepresentation.STANDARD);
                    }
                }
                """,
              spec -> spec.path("src/test/java/com/example/TestMongoConfiguration.java")
            ),
            properties(
              null,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .actual())
            )
          )
        );
    }

    @Test
    void nullJavaConfigurationDoesNotSuppressDiagnostics() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            java(
              """
                package com.example;

                import com.mongodb.MongoClientSettings;
                import org.springframework.data.mongodb.core.convert.MongoCustomConversions.MongoConverterConfigurationAdapter;

                class MongoConfiguration {
                    void configure(MongoClientSettings.Builder builder,
                                   MongoConverterConfigurationAdapter adapter) {
                        builder.uuidRepresentation(null);
                        adapter.bigDecimal(null);
                    }
                }
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .contains("// `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.")
                .contains("builder.uuidRepresentation(null)")
                .contains("// `spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.")
                .contains("adapter.bigDecimal(null)")
                .doesNotContain("~~(")
                .actual())
            )
          )
        );
    }

    @Test
    void explicitUnspecifiedJavaConfigurationDoesNotSuppressDiagnostics() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
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
                        builder.uuidRepresentation(UuidRepresentation.UNSPECIFIED);
                        adapter.bigDecimal(BigDecimalRepresentation.UNSPECIFIED);
                    }
                }
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .contains("// `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.")
                .contains("builder.uuidRepresentation(UuidRepresentation.UNSPECIFIED)")
                .contains("// `spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.")
                .contains("adapter.bigDecimal(BigDecimalRepresentation.UNSPECIFIED)")
                .doesNotContain("~~(")
                .actual())
            )
          )
        );
    }

    @Test
    void testResourceConfigurationDoesNotSuppressMainDiagnostics() {
        rewriteRun(
          spec -> spec.cycles(4).expectedCyclesThatMakeChanges(3),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              """
                spring.mongodb.representation.uuid=standard
                spring.data.mongodb.representation.big-decimal=decimal128
                """,
              spec -> spec.path("src/test/resources/application.properties")
            ),
            properties(
              null,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .contains("spring.data.mongodb.representation.big-decimal=UNSPECIFIED")
                  .actual())
            )
          )
        );
    }

    @Test
    void unrelatedMainResourceIsNotUsedAsConfigurationTarget() {
        rewriteRun(
          spec -> spec.cycles(4).expectedCyclesThatMakeChanges(3),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            yaml(
              """
                logging:
                  level: INFO
                """,
              spec -> spec.path("src/main/resources/logback.yml")
            ),
            properties(
              null,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .contains("spring.data.mongodb.representation.big-decimal=UNSPECIFIED")
                  .actual())
            )
          )
        );
    }

    @Test
    void malformedYamlValuesMarkExistingEntries() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            yaml(
              """
                spring:
                  mongodb:
                    representation:
                      uuid:
                        unsupported: value
                  data:
                    mongodb:
                      representation:
                        big-decimal:
                          - decimal128
                """,
              """
                spring:
                  mongodb:
                    representation:
                      # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                      uuid:
                        unsupported: value
                  data:
                    mongodb:
                      representation:
                        # `spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.
                        big-decimal:
                          - decimal128
                """,
              spec -> spec
                .path("src/main/resources/application.yml")
                .afterRecipe(file -> {
                    assertYamlEntryMarked(file, "uuid");
                    assertYamlEntryMarked(file, "big-decimal");
                })
            )
          )
        );
    }

    @Test
    void reportsSharedDeclarationWhenOneBigIntegerIsNotAnId() {
        rewriteRun(
          spec -> spec.cycles(4).expectedCyclesThatMakeChanges(3)
            .dataTable(MongoValueRepresentationFields.Row.class, rows ->
              assertThat(rows)
                .singleElement()
                .extracting(MongoValueRepresentationFields.Row::getField)
                .isEqualTo("sequence")),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.math.BigInteger;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private BigInteger id, sequence;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              null,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.data.mongodb.representation.big-decimal=UNSPECIFIED")
                  .actual())
            )
          )
        );
    }

    @Test
    void isIdempotent() {
        rewriteRun(
          spec -> spec.cycles(4).expectedCyclesThatMakeChanges(3),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private UUID externalId;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              null,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.mongodb.representation.uuid=UNSPECIFIED")
                  .actual())
            )
          )
        );
    }

    @Test
    void sourceWithoutJavaProjectMarkerIsIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              import java.util.UUID;
              import org.springframework.data.mongodb.core.mapping.Document;

              @Document
              class Account {
                  private UUID externalId;
              }
              """
          )
        );
    }

    @Test
    void faultyJavaConfigurationForOneKindCoexistsWithBaselineGenerationForAnother() {
        rewriteRun(
          spec -> spec.cycles(4).expectedCyclesThatMakeChanges(3),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            java(
              """
                package com.example;

                import com.mongodb.MongoClientSettings;

                class MongoConfiguration {
                    void configure(MongoClientSettings.Builder builder) {
                        builder.uuidRepresentation(null);
                    }
                }
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .contains("// `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.")
                .contains("builder.uuidRepresentation(null)")
                .doesNotContain("~~(")
                .actual())
            ),
            properties(
              null,
              spec -> spec
                .path("src/main/resources/application.properties")
                .after(actual -> assertThat(actual)
                  .contains("spring.data.mongodb.representation.big-decimal=UNSPECIFIED")
                  .doesNotContain("spring.mongodb.representation.uuid")
                  .actual())
            )
          )
        );
    }

    @Test
    void doesNotDuplicateAnAlreadyCommentedOutPlaceholderSuggestion() {
        // Simulates the steady state after a prior cycle already created and commented the suggested
        // property (an active, real UNSPECIFIED value — see
        // MongoValueRepresentationDiagnostics.addUnspecifiedPropertySuggestion's javadoc). Scanning
        // must recognize it as an existing attempt (not
        // "unattempted"), otherwise propertiesToAdd would re-include the kind and produce a second,
        // duplicate suggestion on top of the first.
        rewriteRun(
          spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private UUID externalId;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              """
                # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                spring.mongodb.representation.uuid=UNSPECIFIED
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void doesNotDuplicateAnAlreadyCommentedOutPlaceholderSuggestionInYaml() {
        // YAML equivalent of doesNotDuplicateAnAlreadyCommentedOutPlaceholderSuggestion: the
        // suggestion is a real tree node (see mergeYamlSuggestion), so a prior run's suggestion is
        // visible to FindProperty like any other entry — scanYamlProperty must recognize its
        // UNSPECIFIED value as an ordinary invalid configuration rather than re-suggesting it.
        rewriteRun(
          spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private UUID externalId;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            yaml(
              """
                spring:
                  application:
                    name: example
                  mongodb:
                    representation:
                      # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                      uuid: UNSPECIFIED
                """,
              spec -> spec.path("src/main/resources/application.yml")
            )
          )
        );
    }

    @Test
    void doesNotDuplicateAnAlreadyCommentedInvalidPropertyMessage() {
        // Simulates a separate, later recipe invocation over a file a prior run already annotated:
        // the still-invalid entry is visible to the scanner every time (unlike the missing-property
        // placeholder, it never becomes a Properties.Comment), so this exercises Comments.of(...)'s
        // own documented idempotency rather than any guard of ours. No SearchResult is applied to
        // the entry itself (see MongoValueRepresentationDiagnostics), so re-running leaves the file
        // byte-for-byte unchanged; PropertiesCommentService still returns a structurally-new (but
        // textually identical) tree on the re-add, so one cycle is still needed to reach that state.
        rewriteRun(
          spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            properties(
              """
                # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                spring.mongodb.representation.uuid=unsupported
                spring.data.mongodb.representation.big-decimal=decimal128
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void doesNotDuplicateAnAlreadyCommentedInvalidPropertyMessageInYaml() {
        // YAML equivalent of doesNotDuplicateAnAlreadyCommentedInvalidPropertyMessage, confirming
        // Comments.of(...)'s idempotency also holds for the YAML CommentService implementation
        // (same structurally-new-but-textually-identical-tree behavior noted there).
        rewriteRun(
          spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            yaml(
              """
                spring:
                  mongodb:
                    representation:
                      # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                      uuid: unsupported
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
    void doesNotDuplicateAnAlreadyCommentedInvalidJavaConfigurationMessage() {
        // Java equivalent of doesNotDuplicateAnAlreadyCommentedInvalidPropertyMessage, confirming
        // Comments.of(...)'s idempotency also holds for the Java CommentService implementation.
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal()),
            java(
              """
                package com.example;

                import com.mongodb.MongoClientSettings;

                class MongoConfiguration {
                    void configure(MongoClientSettings.Builder builder) {
                        // `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
                        builder.uuidRepresentation(null);
                    }
                }
                """,
              spec -> spec.path("src/main/java/com/example/MongoConfiguration.java")
            )
          )
        );
    }
}

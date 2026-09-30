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
import org.openrewrite.test.RecipeSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

/**
 * Boundaries of what counts as an occurrence and what counts as already configured. Only
 * data-table membership is asserted here; the corresponding file-mutation behavior is covered by
 * {@code AddMongoValueRepresentationPropertyBoundariesTest}.
 */
class FindMissingMongoValueRepresentationBoundariesTest extends MongoValueRepresentationTestSupport {

    @Override
    public void defaults(RecipeSpec spec) {
        super.defaults(spec);
        spec.recipe(new FindMissingMongoValueRepresentation());
    }

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
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows ->
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
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.DBRef;
                import org.springframework.data.mongodb.core.mapping.DocumentReference;
                import org.springframework.data.mongodb.core.mapping.MongoId;

                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/@MongoId
                    private UUID mongoId;

                    /*~~(missing MongoDB value representation configuration)~~>*/@DocumentReference
                    private UUID reference;

                    /*~~(missing MongoDB value representation configuration)~~>*/@DBRef
                    private UUID linked;
                }
                """
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
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows ->
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
              """
                package com.example;

                import java.math.BigDecimal;
                import java.util.List;
                import java.util.Map;
                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/private List<UUID> externalIds;
                    private Map<UUID, String> labelsByExternalId;
                    /*~~(missing MongoDB value representation configuration)~~>*/private Map<String, BigDecimal> balances;
                }
                """
            )
          )
        );
    }

    @Test
    void reportsArrayTypedFields() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows ->
            assertThat(rows)
              .extracting(MongoValueRepresentationFields.Row::getField)
              .containsExactlyInAnyOrder("externalIds")),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private UUID[] externalIds;
                }
                """,
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/private UUID[] externalIds;
                }
                """
            )
          )
        );
    }

    @Test
    void explicitImplicitFieldTargetTypeIsStillReported() {
        // FieldType.IMPLICIT is the default value; setting it explicitly is not an override.
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows ->
            assertThat(rows)
              .extracting(MongoValueRepresentationFields.Row::getField)
              .containsExactlyInAnyOrder("balance")),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.math.BigDecimal;
                import org.springframework.data.mongodb.core.mapping.Document;
                import org.springframework.data.mongodb.core.mapping.Field;
                import org.springframework.data.mongodb.core.mapping.FieldType;

                @Document
                class Account {
                    @Field(targetType = FieldType.IMPLICIT)
                    private BigDecimal balance;
                }
                """,
              """
                package com.example;

                import java.math.BigDecimal;
                import org.springframework.data.mongodb.core.mapping.Document;
                import org.springframework.data.mongodb.core.mapping.Field;
                import org.springframework.data.mongodb.core.mapping.FieldType;

                @Document
                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/@Field(targetType = FieldType.IMPLICIT)
                    private BigDecimal balance;
                }
                """
            )
          )
        );
    }

    @Test
    void invalidJavaConfigurationDoesNotSuppressReporting() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(1)),
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
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/private UUID externalId;
                }
                """,
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
                """
            )
          )
        );
    }

    @Test
    void testResourceConfigurationDoesNotSuppressMainReporting() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              """
                package com.example;

                import java.math.BigDecimal;
                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/private UUID externalId;
                    /*~~(missing MongoDB value representation configuration)~~>*/private BigDecimal balance;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            properties(
              """
                spring.mongodb.representation.uuid=standard
                spring.data.mongodb.representation.big-decimal=decimal128
                """,
              spec -> spec.path("src/test/resources/application.properties")
            )
          )
        );
    }

    @Test
    void unrelatedMainResourceIsNotUsedAsConfigurationTarget() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              """
                package com.example;

                import java.math.BigDecimal;
                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/private UUID externalId;
                    /*~~(missing MongoDB value representation configuration)~~>*/private BigDecimal balance;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            ),
            yaml(
              """
                logging:
                  level: INFO
                """,
              spec -> spec.path("src/main/resources/logback.yml")
            )
          )
        );
    }

    @Test
    void malformedYamlValuesDoNotSuppressReporting() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows -> assertThat(rows).hasSize(2)),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              accountWithUuidAndBigDecimal(),
              """
                package com.example;

                import java.math.BigDecimal;
                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/private UUID externalId;
                    /*~~(missing MongoDB value representation configuration)~~>*/private BigDecimal balance;
                }
                """
            ),
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
              spec -> spec.path("src/main/resources/application.yml")
            )
          )
        );
    }

    @Test
    void reportsSharedDeclarationWhenOneBigIntegerIsNotAnId() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows ->
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
              """
                package com.example;

                import java.math.BigInteger;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/private BigInteger id, sequence;
                }
                """
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
}

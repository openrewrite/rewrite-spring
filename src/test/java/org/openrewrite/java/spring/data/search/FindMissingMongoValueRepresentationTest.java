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
import org.openrewrite.java.spring.data.AddMongoValueRepresentationProperty;
import org.openrewrite.java.spring.table.MongoValueRepresentationFields;
import org.openrewrite.test.RecipeSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

/**
 * Verifies the search half only: {@code SearchResult} markers and the data table. No file is
 * ever mutated by this recipe — that's {@link AddMongoValueRepresentationProperty}.
 */
class FindMissingMongoValueRepresentationTest extends MongoValueRepresentationTestSupport {

    @Override
    public void defaults(RecipeSpec spec) {
        super.defaults(spec);
        spec.recipe(new FindMissingMongoValueRepresentation());
    }

    @DocumentExample
    @Test
    void marksAffectedFieldsAndListsThemInTheDataTable() {
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows ->
            assertThat(rows)
              .extracting(
                MongoValueRepresentationFields.Row::getOwningType,
                MongoValueRepresentationFields.Row::getField,
                MongoValueRepresentationFields.Row::getConfigurationProperty)
              .containsExactlyInAnyOrder(
                tuple("com.example.Account", "externalId", "spring.mongodb.representation.uuid"),
                tuple("com.example.Account", "balance", "spring.data.mongodb.representation.big-decimal"),
                tuple("com.example.Account", "sequence", "spring.data.mongodb.representation.big-decimal"))),
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
              """
                package com.example;

                import java.math.BigDecimal;
                import java.math.BigInteger;
                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(missing MongoDB value representation configuration)~~>*/private UUID externalId;
                    /*~~(missing MongoDB value representation configuration)~~>*/private BigDecimal balance;
                    /*~~(missing MongoDB value representation configuration)~~>*/private BigInteger sequence;
                }
                """,
              spec -> spec.path("src/main/java/com/example/Account.java")
            )
          )
        );
    }

    @Test
    void javaConfigurationSuppressesFurtherReporting() {
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
    void propertiesConfigurationSuppressesFurtherReporting() {
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
    void legacyUuidPropertyKeySuppressesReporting() {
        // Renamed by UpgradeSpringBoot_4_0 to spring.mongodb.representation.uuid; a project that
        // hasn't gone through that migration yet may still carry the old key.
        rewriteRun(
          spec -> spec.dataTable(MongoValueRepresentationFields.Row.class, rows ->
            assertThat(rows).extracting(MongoValueRepresentationFields.Row::getField)
              .containsExactly("balance")),
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(
              """
                package com.example;

                import java.math.BigDecimal;
                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private UUID externalId;
                    private BigDecimal balance;
                }
                """,
              """
                package com.example;

                import java.math.BigDecimal;
                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    private UUID externalId;
                    /*~~(missing MongoDB value representation configuration)~~>*/private BigDecimal balance;
                }
                """
            ),
            properties(
              """
                spring.data.mongodb.uuid-representation=java-legacy
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void yamlConfigurationSuppressesFurtherReporting() {
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
}

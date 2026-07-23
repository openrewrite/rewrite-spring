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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class FindMissingMongoValueRepresentationTest implements RewriteTest {

    private static final String MINIMAL_POM =
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
              </project>
              """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindMissingMongoValueRepresentation())
          .parser(JavaParser.fromJavaVersion().dependsOn(
            """
              package org.springframework.data.mongodb.core.mapping;
              public @interface Document {}
              """,
            """
              package org.springframework.data.annotation;
              public @interface Persistent {}
              """,
            """
              package org.springframework.data.mongodb.core.mapping;
              public enum FieldType { IMPLICIT, STRING, DECIMAL128, OBJECT_ID }
              """,
            """
              package org.springframework.data.mongodb.core.mapping;
              public @interface Field {
                  FieldType targetType() default FieldType.IMPLICIT;
              }
              """,
            """
              package org.springframework.data.mongodb.core.mapping;
              public @interface MongoId {
                  FieldType value() default FieldType.IMPLICIT;
              }
              """,
            """
              package org.springframework.data.mongodb.core.mapping;
              public @interface DBRef {}
              """,
            """
              package org.springframework.data.mongodb.core.mapping;
              public @interface DocumentReference {}
              """,
            """
              package org.springframework.data.annotation;
              public @interface Transient {}
              """,
            """
              package org.springframework.data.annotation;
              public @interface Id {}
              """,
            """
              package org.bson;
              public enum UuidRepresentation { UNSPECIFIED, STANDARD, JAVA_LEGACY }
              """,
            """
              package com.mongodb;
              public final class MongoClientSettings {
                  public static final class Builder {
                      public Builder uuidRepresentation(org.bson.UuidRepresentation representation) {
                          return this;
                      }
                  }
              }
              """,
            """
              package org.springframework.data.mongodb.core.convert;
              public class MongoCustomConversions {
                  public enum BigDecimalRepresentation { UNSPECIFIED, STRING, DECIMAL128 }
                  public static class MongoConverterConfigurationAdapter {
                      public MongoConverterConfigurationAdapter bigDecimal(BigDecimalRepresentation representation) {
                          return this;
                      }
                  }
              }
              """
          ));
    }

    @DocumentExample
    @Test
    void reportsUnconfiguredPersistentFields() {
        rewriteRun(
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
                    /*~~(Spring Data MongoDB 5 requires an explicit UUID representation; configure `spring.mongodb.representation.uuid` or `MongoClientSettings.Builder.uuidRepresentation(...)`.)~~>*/private UUID externalId;
                    /*~~(Spring Data MongoDB 5 requires an explicit BigDecimal/BigInteger representation; configure `spring.data.mongodb.representation.big-decimal` or `MongoConverterConfigurationAdapter.bigDecimal(...)`.)~~>*/private BigDecimal balance;
                    /*~~(Spring Data MongoDB 5 requires an explicit BigDecimal/BigInteger representation; configure `spring.data.mongodb.representation.big-decimal` or `MongoConverterConfigurationAdapter.bigDecimal(...)`.)~~>*/private BigInteger sequence;
                }
                """
            )
          )
        );
    }

    @Test
    void javaConfigurationSuppressesDiagnostics() {
        rewriteRun(
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
                """
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
                """
            ),
            properties(
              """
                spring.mongodb.representation.uuid=standard
                spring.data.mongodb.representation.big-decimal=decimal128
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
                """
            ),
            yaml(
              """
                spring:
                  mongodb:
                    representation:
                      uuid: standard
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
    void unspecifiedConfigurationDoesNotSuppressDiagnostics() {
        rewriteRun(
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
                    /*~~(Spring Data MongoDB 5 requires an explicit UUID representation; configure `spring.mongodb.representation.uuid` or `MongoClientSettings.Builder.uuidRepresentation(...)`.)~~>*/private UUID externalId;
                    /*~~(Spring Data MongoDB 5 requires an explicit BigDecimal/BigInteger representation; configure `spring.data.mongodb.representation.big-decimal` or `MongoConverterConfigurationAdapter.bigDecimal(...)`.)~~>*/private BigDecimal balance;
                }
                """
            ),
            properties(
              """
                spring.mongodb.representation.uuid=unspecified
                spring.data.mongodb.representation.big-decimal=UNSPECIFIED
                """,
              spec -> spec.path("src/main/resources/application.properties")
            )
          )
        );
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
    void reportsNestedValuesButNotMapKeys() {
        rewriteRun(
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
                    /*~~(Spring Data MongoDB 5 requires an explicit UUID representation; configure `spring.mongodb.representation.uuid` or `MongoClientSettings.Builder.uuidRepresentation(...)`.)~~>*/private List<UUID> externalIds;
                    private Map<UUID, String> labelsByExternalId;
                    /*~~(Spring Data MongoDB 5 requires an explicit BigDecimal/BigInteger representation; configure `spring.data.mongodb.representation.big-decimal` or `MongoConverterConfigurationAdapter.bigDecimal(...)`.)~~>*/private Map<String, BigDecimal> balances;
                }
                """
            )
          )
        );
    }

    @Test
    void testJavaConfigurationDoesNotSuppressMainDiagnostics() {
        rewriteRun(
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
                    /*~~(Spring Data MongoDB 5 requires an explicit UUID representation; configure `spring.mongodb.representation.uuid` or `MongoClientSettings.Builder.uuidRepresentation(...)`.)~~>*/private UUID externalId;
                }
                """
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
            )
          )
        );
    }

    @Test
    void testResourceConfigurationDoesNotSuppressMainDiagnostics() {
        rewriteRun(
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
                    /*~~(Spring Data MongoDB 5 requires an explicit UUID representation; configure `spring.mongodb.representation.uuid` or `MongoClientSettings.Builder.uuidRepresentation(...)`.)~~>*/private UUID externalId;
                    /*~~(Spring Data MongoDB 5 requires an explicit BigDecimal/BigInteger representation; configure `spring.data.mongodb.representation.big-decimal` or `MongoConverterConfigurationAdapter.bigDecimal(...)`.)~~>*/private BigDecimal balance;
                }
                """
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
    void malformedYamlValuesDoNotSuppressDiagnostics() {
        rewriteRun(
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
                    /*~~(Spring Data MongoDB 5 requires an explicit UUID representation; configure `spring.mongodb.representation.uuid` or `MongoClientSettings.Builder.uuidRepresentation(...)`.)~~>*/private UUID externalId;
                    /*~~(Spring Data MongoDB 5 requires an explicit BigDecimal/BigInteger representation; configure `spring.data.mongodb.representation.big-decimal` or `MongoConverterConfigurationAdapter.bigDecimal(...)`.)~~>*/private BigDecimal balance;
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
                    /*~~(Spring Data MongoDB 5 requires an explicit BigDecimal/BigInteger representation; configure `spring.data.mongodb.representation.big-decimal` or `MongoConverterConfigurationAdapter.bigDecimal(...)`.)~~>*/private BigInteger id, sequence;
                }
                """
            )
          )
        );
    }

    @Test
    void isIdempotent() {
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
              """
                package com.example;

                import java.util.UUID;
                import org.springframework.data.mongodb.core.mapping.Document;

                @Document
                class Account {
                    /*~~(Spring Data MongoDB 5 requires an explicit UUID representation; configure `spring.mongodb.representation.uuid` or `MongoClientSettings.Builder.uuidRepresentation(...)`.)~~>*/private UUID externalId;
                }
                """
            )
          )
        );
    }
}

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

import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * Shared test fixtures for the MongoDB value-representation recipes. Does not select a recipe:
 * each concrete test class picks the recipe(s) it exercises.
 */
public abstract class MongoValueRepresentationTestSupport implements RewriteTest {

    public static final String MINIMAL_POM =
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
        spec.parser(JavaParser.fromJavaVersion().dependsOn(
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
            package org.springframework.context.annotation;
            public @interface Profile {
                String[] value();
            }
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

    public static String accountWithUuidAndBigDecimal() {
        return """
          package com.example;

          import java.math.BigDecimal;
          import java.util.UUID;
          import org.springframework.data.mongodb.core.mapping.Document;

          @Document
          class Account {
              private UUID externalId;
              private BigDecimal balance;
          }
          """;
    }
}

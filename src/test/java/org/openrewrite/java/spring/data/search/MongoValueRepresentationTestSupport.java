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
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.trait.Comments;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

abstract class MongoValueRepresentationTestSupport implements RewriteTest {

    protected static final String MINIMAL_POM =
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

    protected static String accountWithUuidAndBigDecimal() {
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

    protected static void assertPropertyMarked(Properties.File file, String property) {
        AtomicBoolean found = new AtomicBoolean();
        new PropertiesIsoVisitor<AtomicBoolean>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, AtomicBoolean marked) {
                Properties.Entry e = super.visitEntry(entry, marked);
                if (property.equals(e.getKey()) &&
                        Comments.of(getCursor()).getComments().stream().anyMatch(comment -> comment.contains(property))) {
                    marked.set(true);
                }
                return e;
            }
        }.visit(file, found);
        assertThat(found.get()).as("Expected property '%s' to carry a diagnostic comment", property).isTrue();
    }

    protected static void assertYamlEntryMarked(Yaml.Documents file, String key) {
        AtomicBoolean found = new AtomicBoolean();
        new YamlIsoVisitor<AtomicBoolean>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, AtomicBoolean marked) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, marked);
                if (e.getKey() instanceof Yaml.Scalar && key.equals(e.getKey().getValue()) &&
                        Comments.of(getCursor()).getComments().stream().anyMatch(comment -> comment.contains(key))) {
                    marked.set(true);
                }
                return e;
            }
        }.visit(file, found);
        assertThat(found.get()).as("Expected YAML entry '%s' to carry a diagnostic comment", key).isTrue();
    }
}

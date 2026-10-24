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

class AddMongoValueRepresentationPropertyTest extends MongoValueRepresentationTestSupport {

    @Override
    public void defaults(RecipeSpec spec) {
        super.defaults(spec);
        spec.recipe(new AddMongoValueRepresentationProperty());
    }

    @DocumentExample
    @Test
    void preservesBothDefaultsWithoutMappedFields() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(bootApplication(), spec -> spec.path("src/main/java/com/example/Application.java")),
            properties(null, """
              spring.data.mongodb.representation.big-decimal=string
              spring.mongodb.representation.uuid=java-legacy
              """, spec -> spec.path("src/main/resources/application.properties"))
          )
        );
    }

    @Test
    void addsDefaultsForEmbeddedTypeNotAnnotatedAsDocument() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(bootApplication(), spec -> spec.path("src/main/java/com/example/Application.java")),
            java("""
              package com.example;
              import java.util.UUID;
              class EmbeddedValue {
                  UUID externalId;
              }
              """),
            properties(null, """
              spring.data.mongodb.representation.big-decimal=string
              spring.mongodb.representation.uuid=java-legacy
              """, spec -> spec.path("src/main/resources/application.properties"))
          )
        );
    }

    @Test
    void addsOnlyTheMissingProperty() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(bootApplication(), spec -> spec.path("src/main/java/com/example/Application.java")),
            properties("""
              spring.mongodb.representation.uuid=standard
              """, """
              spring.data.mongodb.representation.big-decimal=string
              spring.mongodb.representation.uuid=standard
              """, spec -> spec.path("src/main/resources/application.properties"))
          )
        );
    }

    @Test
    void respectsUnconditionalYamlConfiguration() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(bootApplication(), spec -> spec.path("src/main/java/com/example/Application.java")),
            yaml("""
              spring:
                mongodb:
                  representation:
                    uuid: standard
                data:
                  mongodb:
                    representation:
                      big-decimal: decimal128
              """, spec -> spec.path("src/main/resources/application.yml"))
          )
        );
    }

    @Test
    void doesNotModifyNonBootProject() {
        rewriteRun(
          mavenProject("library",
            pomXml(MINIMAL_POM),
            java(accountWithUuidAndBigDecimal(), spec -> spec.path("src/main/java/com/example/Account.java")),
            properties("""
              spring.application.name=library
              """, spec -> spec.path("src/main/resources/application.properties"))
          )
        );
    }

    @Test
    void doesNotRepeatChangesWhenPropertiesAlreadyPreserveDefaults() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(bootApplication(), spec -> spec.path("src/main/java/com/example/Application.java")),
            properties("""
              spring.data.mongodb.representation.big-decimal=string
              spring.mongodb.representation.uuid=java-legacy
              """, spec -> spec.path("src/main/resources/application.properties"))
          )
        );
    }

    @Test
    void addsDefaultsWithoutBuildMetadataOrBootEntryPoint() {
        rewriteRun(
          mavenProject("app",
            java("""
              package com.example;
              import java.util.UUID;
              class EmbeddedValue {
                  UUID externalId;
              }
              """, spec -> spec.path("src/main/java/com/example/EmbeddedValue.java")),
            properties("""
              server.port=8080
              """, """
              server.port=8080
              spring.data.mongodb.representation.big-decimal=string
              spring.mongodb.representation.uuid=java-legacy
              """, spec -> spec.path("src/main/resources/application.properties"))
          )
        );
    }
}

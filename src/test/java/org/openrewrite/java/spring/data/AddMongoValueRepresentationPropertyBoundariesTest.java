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
import org.openrewrite.java.spring.data.search.MongoValueRepresentationTestSupport;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class AddMongoValueRepresentationPropertyBoundariesTest extends MongoValueRepresentationTestSupport {

    @Override
    public void defaults(RecipeSpec spec) {
        super.defaults(spec);
        spec.recipe(new AddMongoValueRepresentationProperty());
    }

    @Test
    void profileSpecificPropertiesDoNotSuppressBaseDefaults() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(bootApplication(), spec -> spec.path("src/main/java/com/example/Application.java")),
            properties("""
              spring.mongodb.representation.uuid=standard
              spring.data.mongodb.representation.big-decimal=decimal128
              """, spec -> spec.path("src/main/resources/application-prod.properties")),
            properties(null, """
              spring.data.mongodb.representation.big-decimal=string
              spring.mongodb.representation.uuid=java-legacy
              """, spec -> spec.path("src/main/resources/application.properties"))
          )
        );
    }

    @Test
    void profileActivatedPropertiesDocumentDoesNotSuppressBaseDefaults() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(bootApplication(), spec -> spec.path("src/main/java/com/example/Application.java")),
            properties("""
              spring.application.name=example
              #---
              spring.config.activate.on-profile=prod
              spring.mongodb.representation.uuid=standard
              """, """
              spring.application.name=example
              spring.data.mongodb.representation.big-decimal=string
              spring.mongodb.representation.uuid=java-legacy
              #---
              spring.config.activate.on-profile=prod
              spring.mongodb.representation.uuid=standard
              """, spec -> spec.path("src/main/resources/application.properties"))
          )
        );
    }

    @Test
    void profileActivatedYamlDocumentDoesNotSuppressBaseDefaults() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(bootApplication(), spec -> spec.path("src/main/java/com/example/Application.java")),
            yaml("""
              spring:
                application:
                  name: example
              ---
              spring:
                config:
                  activate:
                    on-profile: prod
                mongodb:
                  representation:
                    uuid: standard
              """, """
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
              ---
              spring:
                config:
                  activate:
                    on-profile: prod
                mongodb:
                  representation:
                    uuid: standard
              """, spec -> spec.path("src/main/resources/application.yml"))
          )
        );
    }

    @Test
    void invalidExplicitPropertyIsDiagnosedNotOverwritten() {
        rewriteRun(
          mavenProject("app",
            pomXml(MINIMAL_POM),
            java(bootApplication(), spec -> spec.path("src/main/java/com/example/Application.java")),
            properties("""
              spring.mongodb.representation.uuid=UNSPECIFIED
              """, """
              spring.data.mongodb.representation.big-decimal=string
              # `spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.
              spring.mongodb.representation.uuid=UNSPECIFIED
              """, spec -> spec.path("src/main/resources/application.properties"))
          )
        );
    }
}

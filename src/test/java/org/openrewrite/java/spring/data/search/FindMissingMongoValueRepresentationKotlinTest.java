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
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class FindMissingMongoValueRepresentationKotlinTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindMissingMongoValueRepresentation())
          .parser(KotlinParser.builder().dependsOn("""
            package org.springframework.data.mongodb.core.mapping
            annotation class Document
            """));
    }

    @Test
    void findsUuidInKotlinDocumentPrimaryConstructor() {
        rewriteRun(
          mavenProject("app",
            pomXml(MongoValueRepresentationTestSupport.MINIMAL_POM),
            kotlin("""
              package com.example

              import java.util.UUID
              import org.springframework.data.mongodb.core.mapping.Document

              @Document
              data class Account(val externalId: UUID)
              """, spec -> spec.path("src/main/kotlin/com/example/Account.kt")
                            .after(actual -> assertThat(actual)
                                    .contains("missing MongoDB value representation configuration")
                                    .actual()))
          )
        );
    }
}

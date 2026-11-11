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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeSpringData_3_0Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.data.UpgradeSpringData_3_0")
          .parser(JavaParser.fromJavaVersion().dependsOn(
            //language=java
            """
              package org.springframework.data.annotation;
              @Deprecated
              public @interface PersistenceConstructor {}
              """
          ));
    }

    @DocumentExample
    @Test
    void persistenceConstructorToPersistenceCreator() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.annotation.PersistenceConstructor;

              class Event {
                  private final String summary;

                  @PersistenceConstructor
                  Event(String summary) {
                      this.summary = summary;
                  }
              }
              """,
            """
              import org.springframework.data.annotation.PersistenceCreator;

              class Event {
                  private final String summary;

                  @PersistenceCreator
                  Event(String summary) {
                      this.summary = summary;
                  }
              }
              """
          )
        );
    }
}

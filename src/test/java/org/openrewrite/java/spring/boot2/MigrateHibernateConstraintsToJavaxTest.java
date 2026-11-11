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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateHibernateConstraintsToJavaxTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot2.MigrateHibernateConstraintsToJavax")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "hibernate-validator-6.0.23.Final", "validation-api-2.0.1.Final"));
    }

    @DocumentExample
    @Test
    void migrateEmail() {
        rewriteRun(
          //language=java
          java(
            """
              import org.hibernate.validator.constraints.Email;
              import org.hibernate.validator.constraints.NotEmpty;

              class CreateEventForm {
                  @Email
                  @NotEmpty
                  private String attendeeEmail;
              }
              """,
            """
              import javax.validation.constraints.Email;
              import javax.validation.constraints.NotEmpty;

              class CreateEventForm {
                  @Email
                  @NotEmpty
                  private String attendeeEmail;
              }
              """
          )
        );
    }
}

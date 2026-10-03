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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateHibernateEmailValidatorTest implements RewriteTest {
    @Test
    void migrateDirectEmailValidation() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0")
            .parser(JavaParser.fromJavaVersion().dependsOn(
              """
                package org.hibernate.validator.internal.constraintvalidators.hv;
                public class EmailValidator {
                    public boolean isValid(CharSequence value, Object context) { return true; }
                }
                """)),
          java(
            """
              import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;

              class A {
                  boolean valid(String value) {
                      return new EmailValidator().isValid(value, null);
                  }
              }
              """,
            """
              import org.hibernate.validator.internal.constraintvalidators.bv.EmailValidator;

              class A {
                  boolean valid(String value) {
                      return new EmailValidator().isValid(value, null);
                  }
              }
              """
          )
        );
    }
}

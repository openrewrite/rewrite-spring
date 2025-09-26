/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateDiskSpaceHealthIndicatorConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDiskSpaceHealthIndicatorConstructor())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-actuator-2.1", "spring-core-5"));
    }

    @DocumentExample
    @Test
    void changeDeprecatedConstructor() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.boot.actuate.system.DiskSpaceHealthIndicator;

              class Test {
                  void method() {
                      DiskSpaceHealthIndicator literal = new DiskSpaceHealthIndicator(null, 1L);
                      DiskSpaceHealthIndicator methodInvocation = new DiskSpaceHealthIndicator(null, value());
                      Long arg = 10L;
                      DiskSpaceHealthIndicator variable = new DiskSpaceHealthIndicator(null, arg);
                  }
                  long value() {
                      return 10L;
                  }
              }
              """,
            """
              import org.springframework.boot.actuate.system.DiskSpaceHealthIndicator;
              import org.springframework.util.unit.DataSize;

              class Test {
                  void method() {
                      DiskSpaceHealthIndicator literal = new DiskSpaceHealthIndicator(null, DataSize.ofBytes(1L));
                      DiskSpaceHealthIndicator methodInvocation = new DiskSpaceHealthIndicator(null, DataSize.ofBytes(value()));
                      Long arg = 10L;
                      DiskSpaceHealthIndicator variable = new DiskSpaceHealthIndicator(null, DataSize.ofBytes(arg));
                  }
                  long value() {
                      return 10L;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeCurrentApi() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.actuate.system.DiskSpaceHealthIndicator;
              import org.springframework.util.unit.DataSize;

              class Test {
                  void method() {
                      DiskSpaceHealthIndicator indicator = new DiskSpaceHealthIndicator(null, DataSize.ofBytes(1));
                  }
              }
              """
          )
        );
    }
}

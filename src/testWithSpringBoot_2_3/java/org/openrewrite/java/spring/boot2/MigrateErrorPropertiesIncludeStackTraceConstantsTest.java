/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateErrorPropertiesIncludeStackTraceConstantsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateErrorPropertiesIncludeStackTraceConstants())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot-autoconfigure"));
    }

    @Test
    void doNotUpdateCurrentAPIs() {
        //language=java
        rewriteRun(
          java(
            """
              package org.test;

              import org.springframework.boot.autoconfigure.web.ErrorProperties;

              import static org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace.ALWAYS;
              
              class Test {
                  void methodA() {
                      ErrorProperties.IncludeStacktrace doNotUpdate = ErrorProperties.IncludeStacktrace.NEVER;
                  }
                  void methodB() {
                      ErrorProperties.IncludeStacktrace doNotUpdate = ALWAYS;
                  }
              }
              """
          )
        );
    }

    @Test
    void updateFieldAccessToRecommendedReplacements() {
        //language=java
        rewriteRun(
          java(
            """
              package org.test;

              import org.springframework.boot.autoconfigure.web.ErrorProperties;
              
              class Test {
                  void methodA() {
                      ErrorProperties.IncludeStacktrace value = ErrorProperties.IncludeStacktrace.ON_TRACE_PARAM;
                  }
              }
              """,
            """
              package org.test;

              import org.springframework.boot.autoconfigure.web.ErrorProperties;
              
              class Test {
                  void methodA() {
                      ErrorProperties.IncludeStacktrace value = ErrorProperties.IncludeStacktrace.ON_PARAM;
                  }
              }
              """
          )
        );
    }

    @Test
    void updateEnumAccessToRecommendedReplacements() {
        //language=java
        rewriteRun(
          java(
            """
              package org.test;

              import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace;
              
              class Test {
                  void methodA() {
                      IncludeStacktrace value = IncludeStacktrace.ON_TRACE_PARAM;
                  }
              }
              """,
            """
              package org.test;

              import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace;
              
              class Test {
                  void methodA() {
                      IncludeStacktrace value = IncludeStacktrace.ON_PARAM;
                  }
              }
              """
          )
        );
    }

    @Test
    void updateStaticAccessToRecommendedReplacements() {
        //language=java
        rewriteRun(
          java(
            """
              package org.test;

              import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace;

              import static org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace.ON_TRACE_PARAM;
              
              class Test {
                  void methodA() {
                      IncludeStacktrace value = ON_TRACE_PARAM;
                  }
              }
              """,
            """
              package org.test;

              import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace;

              import static org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace.ON_PARAM;
              
              class Test {
                  void methodA() {
                      IncludeStacktrace value = ON_PARAM;
                  }
              }
              """
          )
        );
    }
}

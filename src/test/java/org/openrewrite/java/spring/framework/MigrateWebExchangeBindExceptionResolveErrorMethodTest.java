/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import static org.openrewrite.java.Assertions.java;

class MigrateWebExchangeBindExceptionResolveErrorMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new MigrateWebExchangeBindExceptionResolveErrorMethod())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-context-6.+",
            "spring-core-6.+",
            "spring-web-6.+"));
    }

    @DocumentExample
    @Test
    void migrateResourceHttpMessageWriterAddHeadersMethod() {
        rewriteRun(
          // language=java
          java(
            """
              import org.springframework.context.MessageSource;
              import java.util.Locale;
              import org.springframework.web.bind.support.WebExchangeBindException;
              import java.util.Map;
              import org.springframework.validation.ObjectError;

              class A {
                  public void handleValidationError(WebExchangeBindException ex, MessageSource messageSource, Locale locale) {
                      Map<ObjectError, String> errorMessages = ex.resolveErrorMessages(messageSource, locale);
                  }
              }
              """,
            """
              import org.springframework.context.MessageSource;
              import java.util.Locale;
              import org.springframework.web.bind.support.WebExchangeBindException;
              import org.springframework.web.util.BindErrorUtils;

              import java.util.Map;
              import org.springframework.validation.ObjectError;

              class A {
                  public void handleValidationError(WebExchangeBindException ex, MessageSource messageSource, Locale locale) {
                      Map<ObjectError, String> errorMessages = BindErrorUtils.resolve(ex.getAllErrors(), messageSource, locale);
                  }
              }
              """
          )
        );
    }

}

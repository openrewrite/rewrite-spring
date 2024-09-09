/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCodeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot", "spring-context", "spring-beans", "spring-web", "spring-webmvc"));
    }

    @Test
    void migrateDropWizardDependenciesPackageNames() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatus;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

              public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {
                      return super.handleExceptionInternal(ex, body, headers, status, request);
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatusCode;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

              public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
                      return super.handleExceptionInternal(ex, body, headers, status, request);
                  }
              }
              """
          )
        );
    }

}

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
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

class MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCodeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot", "spring-context", "spring-beans", "spring-web", "spring-webmvc"));
    }

    @DocumentExample
    @Test
    void migrateHttpStatustoHttpStatusCode() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
              
              class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
              
                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {
                      // Imagine we log or manipulate the status here somehow
                      return super.handleExceptionInternal(ex, body, headers, status, request);
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatusCode;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
              
              class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
              
                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
                      // Imagine we log or manipulate the status here somehow
                      return super.handleExceptionInternal(ex, body, headers, status, request);
                  }
              }
              """
          )
        );
    }

    @Test
    void changeTypeOfValueCallAndRemoveImport() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
              
              class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
              
                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {
                      int value = status.value();
                      return super.handleExceptionInternal(ex, body, headers, status, request);
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatusCode;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
              
              class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
              
                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
                      int value = status.value();
                      return super.handleExceptionInternal(ex, body, headers, status, request);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotChangeEnumUsageAndImport() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
              
              class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
              
                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {
                      HttpStatus enumValue = HttpStatus.INTERNAL_SERVER_ERROR;
                      return super.handleExceptionInternal(ex, body, headers, enumValue, request);
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatus;
              import org.springframework.http.HttpStatusCode;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
              
              class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
              
                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
                      HttpStatus enumValue = HttpStatus.INTERNAL_SERVER_ERROR;
                      return super.handleExceptionInternal(ex, body, headers, enumValue, request);
                  }
              }
              """
          )
        );
    }

    @Test
    void noSuperCallChangeMethodSignature() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
              
              class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
              
                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(
                      Exception ex, Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {
                      return ResponseEntity.ok().build();
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.HttpStatusCode;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.context.request.WebRequest;
              import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
              
              class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
              
                  @Override
                  protected ResponseEntity<Object> handleExceptionInternal(
                      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
                      return ResponseEntity.ok().build();
                  }
              }
              """
          )
        );
    }

}

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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("MethodMayBeStatic")
class MigrateUtf8MediaTypesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateUtf8MediaTypes())
          .parser(JavaParser.fromJavaVersion().classpath("spring-core", "spring-web"));
    }

    @Test
    void updateFieldAccess() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.MediaType;
              
              class A {
                  void method() {
                      MediaType valueA = MediaType.APPLICATION_JSON_UTF8;
                      String valueB = MediaType.APPLICATION_JSON_UTF8_VALUE;
                      MediaType valueC = MediaType.APPLICATION_PROBLEM_JSON_UTF8;
                      String valueD = MediaType.APPLICATION_PROBLEM_JSON_UTF8_VALUE;
                  }
              }
              """,
            """
              import org.springframework.http.MediaType;
              
              class A {
                  void method() {
                      MediaType valueA = MediaType.APPLICATION_JSON;
                      String valueB = MediaType.APPLICATION_JSON_VALUE;
                      MediaType valueC = MediaType.APPLICATION_PROBLEM_JSON;
                      String valueD = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
                  }
              }
              """
          )
        );
    }

    @Test
    void updateStaticConstant() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.MediaType;
              import static org.springframework.http.MediaType.*;
              
              class A {
                  void method() {
                      MediaType valueA = APPLICATION_JSON_UTF8;
                      String valueB = APPLICATION_JSON_UTF8_VALUE;
                      MediaType valueC = APPLICATION_PROBLEM_JSON_UTF8;
                      String valueD = APPLICATION_PROBLEM_JSON_UTF8_VALUE;
                  }
              }
              """,
            """
              import org.springframework.http.MediaType;
              import static org.springframework.http.MediaType.*;
              
              class A {
                  void method() {
                      MediaType valueA = APPLICATION_JSON;
                      String valueB = APPLICATION_JSON_VALUE;
                      MediaType valueC = APPLICATION_PROBLEM_JSON;
                      String valueD = APPLICATION_PROBLEM_JSON_VALUE;
                  }
              }
              """
          )
        );
    }

    @Test
    void updateFullyQualifiedTarget() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.MediaType;
              class A {
                  void method() {
                      MediaType valueA = org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
                      String valueB = org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
                      MediaType valueC = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_UTF8;
                      String valueD = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_UTF8_VALUE;
                  }
              }
              """,
            """
              import org.springframework.http.MediaType;
              class A {
                  void method() {
                      MediaType valueA = org.springframework.http.MediaType.APPLICATION_JSON;
                      String valueB = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
                      MediaType valueC = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
                      String valueD = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
                  }
              }
              """
          )
        );
    }
}

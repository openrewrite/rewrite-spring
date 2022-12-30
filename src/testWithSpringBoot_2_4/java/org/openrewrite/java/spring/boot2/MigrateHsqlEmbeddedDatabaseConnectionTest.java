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

@SuppressWarnings("MethodMayBeStatic")
class MigrateHsqlEmbeddedDatabaseConnectionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateHsqlEmbeddedDatabaseConnection())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot"));
    }

    @Test
    void updateFieldAccess() {
        //language=java 
        rewriteRun(
          java(
            """
              import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
              
              class A {
                  void method() {
                      EmbeddedDatabaseConnection edbc = EmbeddedDatabaseConnection.HSQL;
                  }
              }
              """,
            """
              import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
              
              class A {
                  void method() {
                      EmbeddedDatabaseConnection edbc = EmbeddedDatabaseConnection.HSQLDB;
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
              import static org.springframework.boot.jdbc.EmbeddedDatabaseConnection.*;
                            
              class A {
                  void method() {
                      Object valueA = HSQL;
                  }
              }
              """,
            """
              import static org.springframework.boot.jdbc.EmbeddedDatabaseConnection.*;
                            
              class A {
                  void method() {
                      Object valueA = HSQLDB;
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
              class A {
                  void method() {
                      Object valueA = org.springframework.boot.jdbc.EmbeddedDatabaseConnection.HSQL;
                  }
              }
              """,
            """
              class A {
                  void method() {
                      Object valueA = org.springframework.boot.jdbc.EmbeddedDatabaseConnection.HSQLDB;
                  }
              }
              """
          )
        );
    }
}

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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;

class MigrateHooksToReactorContextPropertyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateHooksToReactorContextProperty())
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package org.springframework.boot.autoconfigure;
                public @interface SpringBootApplication {}
                """,
              """
                package org.springframework.boot;
                public class SpringApplication {
                    public static void run(Class<?> cls, String[] args) {}
                }
                """,
              """
                package reactor.core.publisher;
                public class Hooks {
                    public static void enableAutomaticContextPropagation() {}
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void replaceMethodCallWithProperty() {
        rewriteRun(
          spec -> spec.recipe(new MigrateHooksToReactorContextProperty()),
          java(
            """
              import reactor.core.publisher.Hooks;
              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class MyApplication {
                  public static void main(String[] args) {
                      Hooks.enableAutomaticContextPropagation();
                      SpringApplication.run(MyApplication.class, args);
                  }
              }
              """,
            """
              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class MyApplication {
                  public static void main(String[] args) {
                      SpringApplication.run(MyApplication.class, args);
                  }
              }
              """
          ),
          properties(
            "",
            "spring.reactor.context-propagation=true",
            spec -> spec.path("application.properties")
          )
        );
    }

    @Test
    void shouldNotAddPropertyWhenNoHooksPresent() {
        rewriteRun(
          spec -> spec.recipe(new MigrateHooksToReactorContextProperty()),
          java(
            """
              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class MyApplication {
                  public static void main(String[] args) {
                      // No Hooks.enableAutomaticContextPropagation() here
                      SpringApplication.run(MyApplication.class, args);
                  }
              }
              """
          ),
          properties(
            """
              server.port=8080
              """,
            spec -> spec.path("application.properties")
          )
        );
    }
}

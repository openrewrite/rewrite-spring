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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateSpringRetryToResilienceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.MigrateSpringRetryToResilience")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-retry-2")
            .dependsOn(
              //language=java
              """
                package org.springframework.core.retry;
                @FunctionalInterface
                public interface Retryable<R> {
                    R execute() throws Throwable;
                }
                """,
              //language=java
              """
                package org.springframework.core.retry;
                public class RetryTemplate {
                    public RetryTemplate() {}
                    public <R> R execute(Retryable<R> retryable) throws Exception { return null; }
                    public <R> R invoke(Retryable<R> retryable) { return null; }
                }
                """,
              //language=java
              """
                package org.springframework.core.retry;
                public interface RetryListener {
                }
                """,
              //language=java
              """
                package org.springframework.core.retry;
                public interface RetryOperations {
                }
                """,
              //language=java
              """
                package org.springframework.resilience.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface EnableResilientMethods {
                }
                """,
              //language=java
              """
                package org.springframework.resilience.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Retryable {
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void migrateRetryTemplateImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.retry.support.RetryTemplate;

              class MyService {
                  void doSomething() {
                      RetryTemplate template = new RetryTemplate();
                  }
              }
              """,
            """
              import org.springframework.core.retry.RetryTemplate;

              class MyService {
                  void doSomething() {
                      RetryTemplate template = new RetryTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateRetryCallbackToRetryable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.retry.RetryCallback;

              class MyService {
                  RetryCallback<String, Exception> callback;
              }
              """,
            """
              import org.springframework.core.retry.Retryable;

              class MyService {
                  Retryable<String> callback;
              }
              """
          )
        );
    }

    @Test
    void migrateRetryListener() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.retry.RetryListener;

              class MyListener implements RetryListener {
              }
              """,
            """
              import org.springframework.core.retry.RetryListener;

              class MyListener implements RetryListener {
              }
              """
          )
        );
    }

    @Test
    void migrateEnableRetryAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.retry.annotation.EnableRetry;

              @EnableRetry
              class AppConfig {
              }
              """,
            """
              import org.springframework.resilience.annotation.EnableResilientMethods;

              @EnableResilientMethods
              class AppConfig {
              }
              """
          )
        );
    }

    @Test
    void migrateRetryableAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.retry.annotation.Retryable;

              class MyService {
                  @Retryable
                  String callService() {
                      return "result";
                  }
              }
              """,
            """
              import org.springframework.resilience.annotation.Retryable;

              class MyService {
                  @Retryable
                  String callService() {
                      return "result";
                  }
              }
              """
          )
        );
    }

    @Test
    void removeSpringRetryDependencyFromMaven() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.retry</groupId>
                          <artifactId>spring-retry</artifactId>
                          <version>2.0.12</version>
                      </dependency>
                      <dependency>
                          <groupId>org.springframework</groupId>
                          <artifactId>spring-core</artifactId>
                          <version>7.0.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework</groupId>
                          <artifactId>spring-core</artifactId>
                          <version>7.0.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void noChangeWhenSpringRetryNotUsed() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  void doSomething() {
                      System.out.println("no retry here");
                  }
              }
              """
          )
        );
    }
}

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
package org.openrewrite.java.spring.opentelemetry;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateSleuthAnnotationsToOpenTelemetryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.opentelemetry.MigrateSleuthAnnotationsToOpenTelemetry")
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package org.springframework.cloud.sleuth.annotation;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface NewSpan {
                    String value() default "";
                }
                """,
              """
                package org.springframework.cloud.sleuth.annotation;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.PARAMETER)
                public @interface SpanTag {
                    String value() default "";
                }
                """,
              """
                package org.springframework.cloud.sleuth.annotation;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface ContinueSpan {}
                """,
              """
                package io.opentelemetry.instrumentation.annotations;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface WithSpan {
                    String value() default "";
                }
                """,
              """
                package io.opentelemetry.instrumentation.annotations;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.PARAMETER)
                public @interface SpanAttribute {
                    String value() default "";
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void migrateNewSpanToWithSpan() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.sleuth.annotation.NewSpan;

              public class TracedService {
                  @NewSpan
                  public void tracedMethod() {
                  }
              }
              """,
            """
              import io.opentelemetry.instrumentation.annotations.WithSpan;

              public class TracedService {
                  @WithSpan
                  public void tracedMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateSpanTagToSpanAttribute() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.sleuth.annotation.SpanTag;

              public class TaggedService {
                  public void process(@SpanTag String orderId) {
                  }
              }
              """,
            """
              import io.opentelemetry.instrumentation.annotations.SpanAttribute;

              public class TaggedService {
                  public void process(@SpanAttribute String orderId) {
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateContinueSpanToWithSpan() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.sleuth.annotation.ContinueSpan;

              public class ContinuedService {
                  @ContinueSpan
                  public void continueProcessing() {
                  }
              }
              """,
            """
              import io.opentelemetry.instrumentation.annotations.WithSpan;

              public class ContinuedService {
                  @WithSpan
                  public void continueProcessing() {
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateMultipleAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.sleuth.annotation.NewSpan;
              import org.springframework.cloud.sleuth.annotation.SpanTag;

              public class OrderService {
                  @NewSpan
                  public void createOrder(@SpanTag String customerId) {
                  }
              }
              """,
            """
              import io.opentelemetry.instrumentation.annotations.SpanAttribute;
              import io.opentelemetry.instrumentation.annotations.WithSpan;

              public class OrderService {
                  @WithSpan
                  public void createOrder(@SpanAttribute String customerId) {
                  }
              }
              """
          )
        );
    }
}

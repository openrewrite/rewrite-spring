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

class MigrateDatadogToOpenTelemetryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.opentelemetry.MigrateDatadogToOpenTelemetry")
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package datadog.trace.api;
                public interface Tracer {}
                """,
              """
                package datadog.trace.api;
                public interface Span {}
                """,
              """
                package datadog.trace.api;
                public interface SpanContext {}
                """,
              """
                package datadog.trace.api;
                public class DDTracer implements Tracer {}
                """,
              """
                package datadog.trace.api;
                public class DDSpan implements Span {}
                """,
              """
                package datadog.trace.api;
                public class DDSpanContext implements SpanContext {}
                """,
              """
                package datadog.trace.context;
                public interface TraceScope extends AutoCloseable {}
                """,
              """
                package datadog.trace.api;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface Trace {
                    String operationName() default "";
                    String resourceName() default "";
                }
                """,
              """
                package io.opentelemetry.api.trace;
                public interface Tracer {}
                """,
              """
                package io.opentelemetry.api.trace;
                public interface Span {}
                """,
              """
                package io.opentelemetry.api.trace;
                public interface SpanContext {}
                """,
              """
                package io.opentelemetry.context;
                public interface Scope extends AutoCloseable {}
                """,
              """
                package io.opentelemetry.instrumentation.annotations;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface WithSpan {
                    String value() default "";
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void migrateDatadogTracer() {
        rewriteRun(
          //language=java
          java(
            """
              import datadog.trace.api.Tracer;

              class TracingService {
                  Tracer tracer;
              }
              """,
            """
              import io.opentelemetry.api.trace.Tracer;

              class TracingService {
                  Tracer tracer;
              }
              """
          )
        );
    }

    @Test
    void migrateDatadogSpan() {
        rewriteRun(
          //language=java
          java(
            """
              import datadog.trace.api.Span;

              class SpanService {
                  Span span;
              }
              """,
            """
              import io.opentelemetry.api.trace.Span;

              class SpanService {
                  Span span;
              }
              """
          )
        );
    }

    @Test
    void migrateDatadogSpanContext() {
        rewriteRun(
          //language=java
          java(
            """
              import datadog.trace.api.SpanContext;

              class ContextService {
                  SpanContext context;
              }
              """,
            """
              import io.opentelemetry.api.trace.SpanContext;

              class ContextService {
                  SpanContext context;
              }
              """
          )
        );
    }

    @Test
    void migrateTraceScope() {
        rewriteRun(
          //language=java
          java(
            """
              import datadog.trace.context.TraceScope;

              class ScopedService {
                  TraceScope scope;
              }
              """,
            """
              import io.opentelemetry.context.Scope;

              class ScopedService {
                  Scope scope;
              }
              """
          )
        );
    }

    @Test
    void migrateTraceAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import datadog.trace.api.Trace;

              class TracedService {
                  @Trace
                  public void tracedMethod() {
                  }
              }
              """,
            """
              import io.opentelemetry.instrumentation.annotations.WithSpan;

              class TracedService {
                  @WithSpan
                  public void tracedMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateDDTracer() {
        rewriteRun(
          //language=java
          java(
            """
              import datadog.trace.api.DDTracer;

              class TracerFactory {
                  DDTracer tracer;
              }
              """,
            """
              import io.opentelemetry.api.trace.Tracer;

              class TracerFactory {
                  Tracer tracer;
              }
              """
          )
        );
    }

    @Test
    void migrateDDSpan() {
        rewriteRun(
          //language=java
          java(
            """
              import datadog.trace.api.DDSpan;

              class SpanFactory {
                  DDSpan span;
              }
              """,
            """
              import io.opentelemetry.api.trace.Span;

              class SpanFactory {
                  Span span;
              }
              """
          )
        );
    }
}

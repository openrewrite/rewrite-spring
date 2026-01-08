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

class MigrateOpenTracingToOpenTelemetryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.opentelemetry.MigrateOpenTracingToOpenTelemetry")
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package io.opentracing;
                public interface Tracer {}
                """,
              """
                package io.opentracing;
                public interface Span {}
                """,
              """
                package io.opentracing;
                public interface SpanContext {}
                """,
              """
                package io.opentracing;
                public interface Scope extends AutoCloseable {}
                """,
              """
                package io.opentracing.propagation;
                public interface TextMapExtract {}
                """,
              """
                package io.opentracing.propagation;
                public interface TextMapInject {}
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
                package io.opentelemetry.context.propagation;
                public interface TextMapGetter<C> {}
                """,
              """
                package io.opentelemetry.context.propagation;
                public interface TextMapSetter<C> {}
                """
            )
          );
    }

    @DocumentExample
    @Test
    void migrateTracer() {
        rewriteRun(
          //language=java
          java(
            """
              import io.opentracing.Tracer;

              class OpenTracingService {
                  Tracer tracer;
              }
              """,
            """
              import io.opentelemetry.api.trace.Tracer;

              class OpenTracingService {
                  Tracer tracer;
              }
              """
          )
        );
    }

    @Test
    void migrateSpan() {
        rewriteRun(
          //language=java
          java(
            """
              import io.opentracing.Span;

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
    void migrateSpanContext() {
        rewriteRun(
          //language=java
          java(
            """
              import io.opentracing.SpanContext;

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
    void migrateScope() {
        rewriteRun(
          //language=java
          java(
            """
              import io.opentracing.Scope;

              class ScopedService {
                  Scope scope;
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
    void migratePropagation() {
        rewriteRun(
          //language=java
          java(
            """
              import io.opentracing.propagation.TextMapExtract;
              import io.opentracing.propagation.TextMapInject;

              class PropagationService {
                  TextMapExtract extractor;
                  TextMapInject injector;
              }
              """,
            """
              import io.opentelemetry.context.propagation.TextMapGetter;
              import io.opentelemetry.context.propagation.TextMapSetter;

              class PropagationService {
                  TextMapGetter extractor;
                  TextMapSetter injector;
              }
              """
          )
        );
    }
}

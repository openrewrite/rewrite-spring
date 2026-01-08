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

class MigrateSleuthToMicrometerTracingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot3.MigrateSleuthApiToMicrometerTracing")
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package org.springframework.cloud.sleuth;
                public interface Tracer {}
                """,
              """
                package org.springframework.cloud.sleuth;
                public interface Span {}
                """,
              """
                package org.springframework.cloud.sleuth;
                public interface Baggage {}
                """,
              """
                package org.springframework.cloud.sleuth;
                public interface BaggageInScope {}
                """,
              """
                package org.springframework.cloud.sleuth;
                public interface CurrentTraceContext {}
                """,
              """
                package org.springframework.cloud.sleuth;
                public interface TraceContext {}
                """,
              """
                package io.micrometer.tracing;
                public interface Tracer {}
                """,
              """
                package io.micrometer.tracing;
                public interface Span {}
                """,
              """
                package io.micrometer.tracing;
                public interface Baggage {}
                """,
              """
                package io.micrometer.tracing;
                public interface BaggageInScope {}
                """,
              """
                package io.micrometer.tracing;
                public interface CurrentTraceContext {}
                """,
              """
                package io.micrometer.tracing;
                public interface TraceContext {}
                """
            )
          );
    }

    @DocumentExample
    @Test
    void migrateTracerImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.sleuth.Tracer;

              public class TracingService {
                  private Tracer tracer;
              }
              """,
            """
              import io.micrometer.tracing.Tracer;

              public class TracingService {
                  private Tracer tracer;
              }
              """
          )
        );
    }

    @Test
    void migrateSpanImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.sleuth.Span;

              public class SpanService {
                  private Span span;
              }
              """,
            """
              import io.micrometer.tracing.Span;

              public class SpanService {
                  private Span span;
              }
              """
          )
        );
    }

    @Test
    void migrateBaggage() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.sleuth.Baggage;
              import org.springframework.cloud.sleuth.BaggageInScope;

              public class BaggageService {
                  private Baggage baggage;
                  private BaggageInScope scope;
              }
              """,
            """
              import io.micrometer.tracing.Baggage;
              import io.micrometer.tracing.BaggageInScope;

              public class BaggageService {
                  private Baggage baggage;
                  private BaggageInScope scope;
              }
              """
          )
        );
    }

    @Test
    void migrateCurrentTraceContext() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.sleuth.CurrentTraceContext;
              import org.springframework.cloud.sleuth.TraceContext;

              public class ContextService {
                  private CurrentTraceContext currentContext;
                  private TraceContext traceContext;
              }
              """,
            """
              import io.micrometer.tracing.CurrentTraceContext;
              import io.micrometer.tracing.TraceContext;

              public class ContextService {
                  private CurrentTraceContext currentContext;
                  private TraceContext traceContext;
              }
              """
          )
        );
    }
}

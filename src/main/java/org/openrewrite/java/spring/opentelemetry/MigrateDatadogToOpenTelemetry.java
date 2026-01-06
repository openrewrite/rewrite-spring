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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;

import java.util.Arrays;
import java.util.List;

/**
 * Migrates Datadog tracing API types to OpenTelemetry API equivalents.
 * <p>
 * This recipe handles the migration of:
 * <ul>
 *   <li>Datadog Tracer → OpenTelemetry Tracer</li>
 *   <li>Datadog Span → OpenTelemetry Span</li>
 *   <li>Datadog SpanContext → OpenTelemetry SpanContext</li>
 *   <li>Datadog Scope → OpenTelemetry Scope</li>
 *   <li>Datadog @Trace annotation → OpenTelemetry @WithSpan</li>
 * </ul>
 */
public class MigrateDatadogToOpenTelemetry extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate Datadog tracing to OpenTelemetry";
    }

    @Override
    public String getDescription() {
        return "Migrates Datadog tracing API types and annotations to OpenTelemetry API equivalents. " +
               "This includes migrating the Datadog Tracer, Span, SpanContext, Scope, and @Trace annotation.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
            // Datadog core tracing types to OpenTelemetry
            new ChangeType("datadog.trace.api.Tracer", "io.opentelemetry.api.trace.Tracer", true),
            new ChangeType("io.opentracing.Tracer", "io.opentelemetry.api.trace.Tracer", true),
            new ChangeType("datadog.trace.api.DDTracer", "io.opentelemetry.api.trace.Tracer", true),

            // Span types
            new ChangeType("datadog.trace.api.Span", "io.opentelemetry.api.trace.Span", true),
            new ChangeType("io.opentracing.Span", "io.opentelemetry.api.trace.Span", true),
            new ChangeType("datadog.trace.api.DDSpan", "io.opentelemetry.api.trace.Span", true),

            // SpanContext
            new ChangeType("datadog.trace.api.SpanContext", "io.opentelemetry.api.trace.SpanContext", true),
            new ChangeType("io.opentracing.SpanContext", "io.opentelemetry.api.trace.SpanContext", true),
            new ChangeType("datadog.trace.api.DDSpanContext", "io.opentelemetry.api.trace.SpanContext", true),

            // Scope
            new ChangeType("datadog.trace.context.TraceScope", "io.opentelemetry.context.Scope", true),
            new ChangeType("io.opentracing.Scope", "io.opentelemetry.context.Scope", true),

            // @Trace annotation to @WithSpan
            new ChangeType("datadog.trace.api.Trace", "io.opentelemetry.instrumentation.annotations.WithSpan", true)
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Use the composite recipe approach via getRecipeList()
        return TreeVisitor.noop();
    }
}

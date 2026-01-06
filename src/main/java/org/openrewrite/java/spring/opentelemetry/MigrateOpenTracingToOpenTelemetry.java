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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;

import java.util.Arrays;
import java.util.List;

/**
 * Migrates OpenTracing API to OpenTelemetry API.
 * <p>
 * OpenTracing has been superseded by OpenTelemetry and is no longer actively maintained.
 * This recipe handles the migration of core OpenTracing types to their OpenTelemetry equivalents.
 *
 * @see <a href="https://opentelemetry.io/docs/migration/opentracing/">OpenTracing Migration Guide</a>
 */
public class MigrateOpenTracingToOpenTelemetry extends Recipe {

    private static final String OPENTRACING_PACKAGE = "io.opentracing";
    private static final String OTEL_API_PACKAGE = "io.opentelemetry.api";
    private static final String OTEL_CONTEXT_PACKAGE = "io.opentelemetry.context";

    @Override
    public String getDisplayName() {
        return "Migrate OpenTracing API to OpenTelemetry API";
    }

    @Override
    public String getDescription() {
        return "Migrate OpenTracing API to OpenTelemetry API. " +
               "OpenTracing has been superseded by OpenTelemetry and is no longer actively maintained.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                // Core types
                new ChangeType(OPENTRACING_PACKAGE + ".Tracer", OTEL_API_PACKAGE + ".trace.Tracer", true),
                new ChangeType(OPENTRACING_PACKAGE + ".Span", OTEL_API_PACKAGE + ".trace.Span", true),
                new ChangeType(OPENTRACING_PACKAGE + ".SpanContext", OTEL_API_PACKAGE + ".trace.SpanContext", true),
                new ChangeType(OPENTRACING_PACKAGE + ".Scope", OTEL_CONTEXT_PACKAGE + ".Scope", true),
                new ChangeType(OPENTRACING_PACKAGE + ".ScopeManager", OTEL_CONTEXT_PACKAGE + ".Context", true),

                // Propagation types
                new ChangeType(OPENTRACING_PACKAGE + ".propagation.Format", OTEL_CONTEXT_PACKAGE + ".propagation.TextMapPropagator", true),
                new ChangeType(OPENTRACING_PACKAGE + ".propagation.TextMapExtract", OTEL_CONTEXT_PACKAGE + ".propagation.TextMapGetter", true),
                new ChangeType(OPENTRACING_PACKAGE + ".propagation.TextMapInject", OTEL_CONTEXT_PACKAGE + ".propagation.TextMapSetter", true)
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(OPENTRACING_PACKAGE + ".*", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    // The actual migration is done by the recipe list above
                }
        );
    }
}

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
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.List;

/**
 * Migrates Spring Cloud Sleuth API to Micrometer Tracing API.
 * <p>
 * Spring Cloud Sleuth has been deprecated and is replaced by Micrometer Tracing
 * with OpenTelemetry as the recommended backend.
 *
 * @see <a href="https://github.com/spring-cloud/spring-cloud-sleuth#spring-cloud-sleuth">Spring Cloud Sleuth README</a>
 * @see <a href="https://micrometer.io/docs/tracing">Micrometer Tracing Documentation</a>
 */
public class MigrateSleuthToMicrometerTracing extends Recipe {

    private static final String SLEUTH_PACKAGE = "org.springframework.cloud.sleuth";
    private static final String MICROMETER_TRACING_PACKAGE = "io.micrometer.tracing";

    @Override
    public String getDisplayName() {
        return "Migrate Spring Cloud Sleuth to Micrometer Tracing";
    }

    @Override
    public String getDescription() {
        return "Migrate Spring Cloud Sleuth API to Micrometer Tracing API. " +
               "Spring Cloud Sleuth has been deprecated and is replaced by Micrometer Tracing " +
               "with OpenTelemetry as the recommended backend.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                // Core types migration
                new ChangeType(SLEUTH_PACKAGE + ".Tracer", MICROMETER_TRACING_PACKAGE + ".Tracer", true),
                new ChangeType(SLEUTH_PACKAGE + ".Span", MICROMETER_TRACING_PACKAGE + ".Span", true),
                new ChangeType(SLEUTH_PACKAGE + ".Span$Kind", MICROMETER_TRACING_PACKAGE + ".Span$Kind", true),
                new ChangeType(SLEUTH_PACKAGE + ".SpanCustomizer", MICROMETER_TRACING_PACKAGE + ".SpanCustomizer", true),
                new ChangeType(SLEUTH_PACKAGE + ".CurrentTraceContext", MICROMETER_TRACING_PACKAGE + ".CurrentTraceContext", true),
                new ChangeType(SLEUTH_PACKAGE + ".TraceContext", MICROMETER_TRACING_PACKAGE + ".TraceContext", true),
                new ChangeType(SLEUTH_PACKAGE + ".ScopedSpan", MICROMETER_TRACING_PACKAGE + ".ScopedSpan", true),
                new ChangeType(SLEUTH_PACKAGE + ".SpanAndScope", MICROMETER_TRACING_PACKAGE + ".SpanAndScope", true),

                // Baggage types
                new ChangeType(SLEUTH_PACKAGE + ".Baggage", MICROMETER_TRACING_PACKAGE + ".Baggage", true),
                new ChangeType(SLEUTH_PACKAGE + ".BaggageInScope", MICROMETER_TRACING_PACKAGE + ".BaggageInScope", true),

                // Propagation types
                new ChangeType(SLEUTH_PACKAGE + ".propagation.Propagator", MICROMETER_TRACING_PACKAGE + ".propagation.Propagator", true),

                // Exporter types
                new ChangeType(SLEUTH_PACKAGE + ".exporter.SpanFilter", MICROMETER_TRACING_PACKAGE + ".exporter.SpanExportingPredicate", true),
                new ChangeType(SLEUTH_PACKAGE + ".exporter.SpanReporter", MICROMETER_TRACING_PACKAGE + ".exporter.SpanReporter", true)
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(SLEUTH_PACKAGE + ".*", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    // The actual migration is done by the recipe list above
                    // This visitor serves as a precondition check
                }
        );
    }
}

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
 * Migrates Spring Cloud Sleuth annotations to OpenTelemetry annotations.
 * <p>
 * This recipe handles the migration of:
 * <ul>
 *   <li>{@code @NewSpan} → {@code @WithSpan}</li>
 *   <li>{@code @SpanTag} → {@code @SpanAttribute}</li>
 *   <li>{@code @ContinueSpan} → {@code @WithSpan}</li>
 * </ul>
 *
 * @see <a href="https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/annotations/">OpenTelemetry Annotations</a>
 */
public class MigrateSleuthAnnotationsToOpenTelemetry extends Recipe {

    private static final String SLEUTH_ANNOTATION_PACKAGE = "org.springframework.cloud.sleuth.annotation";
    private static final String OTEL_ANNOTATION_PACKAGE = "io.opentelemetry.instrumentation.annotations";

    @Override
    public String getDisplayName() {
        return "Migrate Sleuth annotations to OpenTelemetry annotations";
    }

    @Override
    public String getDescription() {
        return "Migrate Spring Cloud Sleuth annotations (@NewSpan, @SpanTag, @ContinueSpan) " +
               "to OpenTelemetry annotations (@WithSpan, @SpanAttribute).";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                // @NewSpan -> @WithSpan
                new ChangeType(SLEUTH_ANNOTATION_PACKAGE + ".NewSpan", OTEL_ANNOTATION_PACKAGE + ".WithSpan", true),
                // @SpanTag -> @SpanAttribute
                new ChangeType(SLEUTH_ANNOTATION_PACKAGE + ".SpanTag", OTEL_ANNOTATION_PACKAGE + ".SpanAttribute", true),
                // @ContinueSpan -> @WithSpan (note: semantics differ slightly)
                new ChangeType(SLEUTH_ANNOTATION_PACKAGE + ".ContinueSpan", OTEL_ANNOTATION_PACKAGE + ".WithSpan", true)
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(SLEUTH_ANNOTATION_PACKAGE + ".*", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    // The actual migration is done by the recipe list above
                }
        );
    }
}

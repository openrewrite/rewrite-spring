/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.cloud2022;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateCloudSleuthToMicrometerTracingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.spring.cloud2022.MigrateCloudSleuthToMicrometerTracing"))
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "spring-cloud-sleuth-api"));
    }

    @DocumentExample
    @Test
    void migrateTracer() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.cloud.sleuth.Tracer;
              
              public class SessionInfoOperator {
                  private Tracer tracer;
              
                  public SessionInfoOperator(Tracer tracer) {
                      this.tracer = tracer;
                  }
              
                  public boolean getSessionInfo(String key) {
                      return tracer.currentSpan().isNoop();
                  }
              }
              """,
            """
              import io.micrometer.tracing.Tracer;
              
              public class SessionInfoOperator {
                  private Tracer tracer;
              
                  public SessionInfoOperator(Tracer tracer) {
                      this.tracer = tracer;
                  }
              
                  public boolean getSessionInfo(String key) {
                      return tracer.currentSpan().isNoop();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateSpanFilter() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.cloud.sleuth.exporter.SpanFilter;
              import org.springframework.cloud.sleuth.exporter.SpanIgnoringSpanFilter;

              import java.util.List;
              
              public class SessionInfoOperator {
                  private SpanFilter filter = new SpanIgnoringSpanFilter(List.of(), List.of());
              }
              """,
            """
              import io.micrometer.tracing.exporter.SpanExportingPredicate;
              import io.micrometer.tracing.exporter.SpanIgnoringSpanExportingPredicate;

              import java.util.List;
              
              public class SessionInfoOperator {
                  private SpanExportingPredicate filter = new SpanIgnoringSpanExportingPredicate(List.of(), List.of());
              }
              """
          )
        );
    }

}

/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateDropWizardDependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.spring")
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot3.MigrateDropWizardDependencies"))
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot", "spring-context", "spring-beans"));
    }

    @Test
    void migrateDropWizardDependenciesPackageNames() {
        java(
          """
                import com.codahale.metrics.servlet.InstrumentedFilter;
                import com.codahale.metrics.servlets.MetricsServlet;
            """,
          """
                import io.dropwizard.metrics.servlet.InstrumentedFilter;
                import io.dropwizard.metrics.servlets.MetricsServlet;
            """);
    }
}

/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveLegacyMetricsAutoConfigurationExclusionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "spring-boot-autoconfigure-1.+", "spring-context-5.+")
          .dependsOn(
            "package org.springframework.boot.actuate.autoconfigure; public class MetricFilterAutoConfiguration {}",
            "package org.springframework.boot.actuate.autoconfigure; public class MetricRepositoryAutoConfiguration {}"
          ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0",
      "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0"
    })
    void removeBothObsoleteClassExclusions(String recipe) {
        rewriteRun(
          spec -> spec.recipeFromResources(recipe),
          java(
            """
              import org.springframework.boot.actuate.autoconfigure.MetricFilterAutoConfiguration;
              import org.springframework.boot.actuate.autoconfigure.MetricRepositoryAutoConfiguration;
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication(exclude = { MetricFilterAutoConfiguration.class, MetricRepositoryAutoConfiguration.class })
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              class Application {
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0",
      "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0"
    })
    void preserveUnrelatedClassExclusion(String recipe) {
        rewriteRun(
          spec -> spec.recipeFromResources(recipe),
          java(
            """
              import org.springframework.boot.actuate.autoconfigure.MetricFilterAutoConfiguration;
              import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

              @EnableAutoConfiguration(exclude = { MetricFilterAutoConfiguration.class, OtherConfiguration.class },
                  excludeName = "org.springframework.boot.actuate.autoconfigure.MetricRepositoryAutoConfiguration")
              class Application {
              }
              class OtherConfiguration {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

              @EnableAutoConfiguration(exclude = { OtherConfiguration.class })
              class Application {
              }
              class OtherConfiguration {
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0",
      "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0"
    })
    void preserveUnrelatedNameExclusion(String recipe) {
        rewriteRun(
          spec -> spec.recipeFromResources(recipe),
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication(excludeName = { "org.springframework.boot.actuate.autoconfigure.MetricFilterAutoConfiguration", "com.example.OtherConfiguration", "org.springframework.boot.actuate.autoconfigure.MetricRepositoryAutoConfiguration" })
              class Application {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication(excludeName = { "com.example.OtherConfiguration" })
              class Application {
              }
              """
          )
        );
    }
}

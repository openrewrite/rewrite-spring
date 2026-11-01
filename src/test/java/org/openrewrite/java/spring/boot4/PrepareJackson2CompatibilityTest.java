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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class PrepareJackson2CompatibilityTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot4.PrepareJackson2Compatibility");
    }

    @Test
    void preservesFeatureConfigurationInProperties() {
        rewriteRun(properties(
          """
            spring.jackson.serialization.write-dates-as-timestamps=false
            spring.jackson.deserialization.fail-on-unknown-properties=true
            spring.jackson.default-property-inclusion=non_null
            """,
          """
            spring.jackson2.serialization.write-dates-as-timestamps=false
            spring.jackson2.deserialization.fail-on-unknown-properties=true
            spring.jackson2.default-property-inclusion=non_null
            """,
          source -> source.path("src/main/resources/application.properties").markers(new JavaSourceSet(Tree.randomId(), "main", List.of(), Map.of()))
        ));
    }

    @Test
    void preservesFeatureConfigurationInProfileYaml() {
        rewriteRun(yaml(
          """
            spring:
              jackson:
                serialization:
                  write-dates-as-timestamps: false
            """,
          """
            spring:
              jackson2:
                serialization:
                  write-dates-as-timestamps: false
            """,
          source -> source.path("src/main/resources/application-production.yml").markers(new JavaSourceSet(Tree.randomId(), "main", List.of(), Map.of()))
        ));
    }

    @Test
    void alreadyRetainedJackson2ConfigurationIsUnchanged() {
        rewriteRun(properties(
          "spring.jackson2.serialization.write-dates-as-timestamps=false",
          source -> source.path("src/main/resources/application.properties").markers(new JavaSourceSet(Tree.randomId(), "main", List.of(), Map.of()))
        ));
    }

    @Test
    void relocatesBootJackson2ComponentsWithoutMigratingJacksonTypes() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-3.5")),
          java(
            """
              import org.springframework.boot.jackson.JsonComponent;
              @JsonComponent
              class CustomComponent {}
              """,
            """
              import org.springframework.boot.jackson2.JsonComponent;
              @JsonComponent
              class CustomComponent {}
              """
          )
        );
    }

    @Test
    void compatibilityCompositionDoesNotAdoptJackson3() {
        Recipe recipe = Environment.builder().scanRuntimeClasspath().build()
          .activateRecipes("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0RetainingJackson2");
        Deque<Recipe> recipes = new ArrayDeque<>();
        recipes.add(recipe);
        while (!recipes.isEmpty()) {
            Recipe next = recipes.removeFirst();
            assertThat(next.getName()).isNotEqualTo("org.openrewrite.java.jackson.UpgradeJackson_2_3");
            recipes.addAll(next.getRecipeList());
        }
    }
}

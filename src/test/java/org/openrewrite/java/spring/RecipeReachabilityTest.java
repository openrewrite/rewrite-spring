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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.config.RecipeDescriptor;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

class RecipeReachabilityTest {

    private static final Set<String> ENTRY_POINTS = Set.of(
      "org.openrewrite.java.spring.boot3.SpringBoot33BestPractices",
      "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0",
      "org.openrewrite.java.spring.opentelemetry.MigrateToOpenTelemetry"
    );

    private static final Set<String> STANDALONE_SEARCH_RECIPES = Set.of(
      "org.openrewrite.java.spring.boot2.search.FindIntegrationSchedulerPool",
      "org.openrewrite.java.spring.boot2.search.FindUpgradeRequirementsSpringBoot_2_5",
      "org.openrewrite.java.spring.http.SpringWebDependency",
      "org.openrewrite.java.spring.search.FindApiCalls",
      "org.openrewrite.java.spring.search.FindApiEndpoints",
      "org.openrewrite.java.spring.search.FindConfigurationProperties",
      "org.openrewrite.java.spring.search.FindSpringComponents",
      "org.openrewrite.java.spring.security5.search.FindEncryptorsQueryableTextUses"
    );

    private static final Set<String> STANDALONE_RECIPES = Set.of(
      "org.openrewrite.java.spring.ConvertAutoConfigurationExcludeToExcludeName",
      "org.openrewrite.java.spring.ExpandProperties",
      "org.openrewrite.java.spring.RenameBean",
      "org.openrewrite.java.spring.UpdateApiManifest",
      "org.openrewrite.java.spring.boot3.AddRouteTrailingSlash",
      "org.openrewrite.java.spring.boot3.AddSetUseTrailingSlashMatch",
      "org.openrewrite.java.spring.boot3.MaintainTrailingSlashURLMappings",
      "org.openrewrite.java.spring.mvc.JaxrsToSpringMvcMediaType",
      "org.openrewrite.java.spring.mvc.JaxrsToSpringMvcResponseEntity",
      "org.openrewrite.maven.spring.UpgradeExplicitSpringBootDependencies"
    );

    private static final Set<String> DELIBERATELY_LEFT_OUT_OF_COMPOSITES = Set.of(
      "org.openrewrite.gradle.spring.AddSpringDependencyManagementPlugin",
      "org.openrewrite.java.spring.ImplicitWebAnnotationNames",
      "org.openrewrite.java.spring.MarkAdditionalSpringConfigFiles",
      "org.openrewrite.java.spring.security6.RemoveOauth2LoginConfig"
    );

    private static final Set<String> NOT_YET_IN_A_COMPOSITE = Set.of(
      "org.openrewrite.java.spring.boot4.SpringBootProperties_4_1",
      "org.openrewrite.java.spring.data.UpgradeSpringData_4_0"
    );

    @Test
    void everyRecipeIsReachableFromAComposite() {
        Collection<RecipeDescriptor> descriptors = Environment.builder().scanRuntimeClasspath().build().listRecipeDescriptors();
        Set<String> referenced = new HashSet<>();
        descriptors.forEach(d -> collectChildren(d, referenced));

        Set<String> unreachable = descriptors.stream()
          .filter(d -> declaredInThisModule(d.getSource()))
          .map(RecipeDescriptor::getName)
          .filter(name -> !referenced.contains(name))
          .collect(toSet());

        assertThat(unreachable)
          .as("Include new recipes in a composite such as an Upgrade* or *BestPractices recipe, " +
              "so users find them without having to seek them out; " +
              "drop recipes from the allowed sets in this test once they are included")
          .containsExactlyInAnyOrderElementsOf(Stream.of(ENTRY_POINTS, STANDALONE_SEARCH_RECIPES, STANDALONE_RECIPES, DELIBERATELY_LEFT_OUT_OF_COMPOSITES, NOT_YET_IN_A_COMPOSITE)
            .flatMap(Set::stream)
            .toList());
    }

    private static boolean declaredInThisModule(URI source) {
        return source != null && "file".equals(source.getScheme()) && !source.getPath().endsWith(".jar");
    }

    private static void collectChildren(RecipeDescriptor descriptor, Set<String> referenced) {
        Stream.concat(descriptor.getPreconditions().stream(), descriptor.getRecipeList().stream())
          .filter(child -> referenced.add(child.getName()))
          .forEach(child -> collectChildren(child, referenced));
    }
}

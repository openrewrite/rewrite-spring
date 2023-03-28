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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.config.RecipeDescriptor;

import static org.junit.jupiter.api.Assertions.*;

class RecipeDescriptorTest {

    private static Environment env;

    @BeforeAll
    static void setupAll() {
        env = Environment.builder().scanRuntimeClasspath().build();
    }

    @Test
    void createRecipeTest() {
        Recipe r = env.listRecipes().stream().filter(d -> "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0".equals(d.getName())).findFirst().orElseThrow();
        RecipeDescriptor recipeDescriptor = r.getDescriptor();
        assertNotNull(recipeDescriptor);

        assertEquals("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0", recipeDescriptor.getName());
        assertEquals(
          "Migrate applications to the latest Spring Boot 3.0 release. This recipe will modify an application's build files, make changes to deprecated/preferred APIs, and migrate configuration settings that have changes between versions. This recipe will also chain additional framework migrations (Spring Framework, Spring Data, etc) that are required as part of the migration to Spring Boot 2.7.\n",
          recipeDescriptor.getDescription());
        assertEquals("Migrate to Spring Boot 3.0", recipeDescriptor.getDisplayName());
        assertTrue(recipeDescriptor.getRecipeList().size() >= 12);

        RecipeDescriptor pomRecipe = recipeDescriptor.getRecipeList().get(2);
        assertEquals("org.openrewrite.java.spring.boot3.MavenPomUpgrade", pomRecipe.getName());
        assertEquals("Upgrade Maven POM to Spring Boot 3.0 from prior 2.x version.", pomRecipe.getDescription());
        assertEquals("Upgrade Maven POM to Spring Boot 3.0 from 2.x", pomRecipe.getDisplayName());
        assertTrue(pomRecipe.getRecipeList().size() >= 3);

    }
}
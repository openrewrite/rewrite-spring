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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeSpringPropertyKeyDeclarationsTest {

    private static final Set<String> NEW_PROPERTY_KEYS = new TreeSet<>();

    @BeforeAll
    static void collectNewPropertyKeys() {
        Environment.builder().scanRuntimeClasspath().build().listRecipeDescriptors().stream()
          .filter(recipe -> recipe.getName().startsWith("org.openrewrite.java.spring."))
          .flatMap(recipe -> recipe.getRecipeList().stream())
          .forEach(ChangeSpringPropertyKeyDeclarationsTest::collect);
        assertThat(NEW_PROPERTY_KEYS).isNotEmpty();
    }

    private static void collect(RecipeDescriptor recipe) {
        if (ChangeSpringPropertyKey.class.getName().equals(recipe.getName())) {
            for (OptionDescriptor option : recipe.getOptions()) {
                if ("newPropertyKey".equals(option.getName())) {
                    NEW_PROPERTY_KEYS.add(String.valueOf(option.getValue()));
                }
            }
        }
        recipe.getRecipeList().forEach(ChangeSpringPropertyKeyDeclarationsTest::collect);
    }

    @Test
    void newPropertyKeysAreQualified() {
        assertThat(NEW_PROPERTY_KEYS).filteredOn(key -> !key.contains(".")).isEmpty();
    }

    @Test
    void newPropertyKeysAreKebabCase() {
        assertThat(NEW_PROPERTY_KEYS)
          .filteredOn(key -> !key.matches("[a-z0-9.\\-\\[\\]*]+"))
          // As listed in the Spring Boot 3.4 metadata; relaxed binding still maps it to `max-delay`
          .containsExactly("spring.kafka.retry.topic.backoff.maxDelay");
    }
}

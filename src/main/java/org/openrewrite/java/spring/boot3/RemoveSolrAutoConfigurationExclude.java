/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import lombok.Getter;
import org.openrewrite.Recipe;
import org.openrewrite.java.spring.RemoveAutoConfigurationExclude;

import java.util.List;

import static java.util.Collections.singletonList;

public class RemoveSolrAutoConfigurationExclude extends Recipe {

    @Getter
    final String displayName = "Remove `SolrAutoConfiguration`";

    @Getter
    final String description = "`SolrAutoConfiguration` was removed in Spring Boot 3; remove references to it from exclusions on annotations.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(new RemoveAutoConfigurationExclude("org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration"));
    }
}

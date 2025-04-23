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
package org.openrewrite.java.spring.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class SpringComponentRelationships extends DataTable<SpringComponentRelationships.Row> {

    public SpringComponentRelationships(Recipe recipe) {
        super(recipe,
                "Relationships between Spring components",
                "A table of relationships between Spring components.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Defined in source file",
                description = "The source file that provides evidence of the relationship between dependant and dependency.")
        String sourceFile;

        @Column(displayName = "Dependant type",
                description = "The type of the component requiring a collaborator.")
        String dependantType;

        @Column(displayName = "Dependency type",
                description = "The type of the component that is being injected.")
        String dependencyType;
    }
}

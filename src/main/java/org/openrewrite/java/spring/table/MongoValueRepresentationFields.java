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
package org.openrewrite.java.spring.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class MongoValueRepresentationFields extends DataTable<MongoValueRepresentationFields.Row> {

    public MongoValueRepresentationFields(Recipe recipe) {
        super(recipe, "MongoDB value representation fields",
                "MongoDB-persisted fields that require an explicit value representation when migrating to Spring Data MongoDB 5.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file containing the affected field.")
        String sourcePath;

        @Column(displayName = "Owning type",
                description = "The fully qualified name of the MongoDB-persisted type.")
        String owningType;

        @Column(displayName = "Field",
                description = "The affected field name.")
        String field;

        @Column(displayName = "Value type",
                description = "The value representation category that requires configuration.")
        String valueType;

        @Column(displayName = "Configuration property",
                description = "The Spring configuration property that can supply the project-wide representation.")
        String configurationProperty;
    }
}

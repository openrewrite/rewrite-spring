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
public class ConfigurationPropertiesTable extends DataTable<ConfigurationPropertiesTable.Row> {

    public ConfigurationPropertiesTable(Recipe recipe) {
        super(recipe, "Configuration properties",
                "Classes annotated with `@ConfigurationProperties` and their prefix values.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file containing the @ConfigurationProperties annotation.")
        String sourcePath;

        @Column(displayName = "Class type",
                description = "The fully qualified name of the class annotated with @ConfigurationProperties.")
        String classType;

        @Column(displayName = "Prefix",
                description = "The prefix/value attribute of the @ConfigurationProperties annotation.")
        String prefix;
    }
}

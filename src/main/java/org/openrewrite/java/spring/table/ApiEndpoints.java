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
public class ApiEndpoints extends DataTable<ApiEndpoints.Row> {

    public ApiEndpoints(Recipe recipe) {
        super(recipe, Row.class, ApiEndpoints.class.getName(),
                "API endpoints", "The API endpoints that applications expose.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file containing the API endpoint definition.")
        String sourcePath;

        @Column(displayName = "Method name",
                description = "The name of the method that defines the API endpoint.")
        String methodName;

        @Column(displayName = "Method",
                description = "The HTTP method of the API endpoint.")
        String method;

        @Column(displayName = "Path",
                description = "The path of the API endpoint.")
        String path;
    }
}

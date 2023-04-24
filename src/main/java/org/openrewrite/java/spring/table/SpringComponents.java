/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class SpringComponents extends DataTable<SpringComponents.Row> {

    public SpringComponents(Recipe recipe) {
        super(recipe, Row.class, SpringComponents.class.getName(),
                "Spring component definitions",
                "Classes defined with a form of a Spring `@Component` stereotype and types returned from `@Bean` annotated methods.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file containing the component definition.")
        String sourcePath;

        @Column(displayName = "Component type",
                description = "The type of the component.")
        String componentType;
    }
}

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
package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.properties.DeleteProperty;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

/**
 * A recipe to remove a property (or matching property group) from Spring configuration files. This recipe supports deleting properties from
 * ".properties" and YAML files.
 */
@EqualsAndHashCode(callSuper = false)
@Value
public class DeleteSpringProperty extends Recipe {

    @Option(displayName = "Property key",
            description = "The property key to delete. Supports glob expressions",
            example = "management.endpoint.configprops.*")
    String propertyKey;

    String displayName = "Delete a spring configuration property";

    String description = "Delete a spring configuration property from any configuration file that contains a matching key.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        org.openrewrite.yaml.DeleteProperty yamlDeleteProperty =
                new org.openrewrite.yaml.DeleteProperty(propertyKey, false, true, null);
        DeleteProperty propertiesDeleteProperty =
                new DeleteProperty(propertyKey, true);

        String descendantPropertyKey = containsGlob(propertyKey) ? null : propertyKey + ".*";
        org.openrewrite.yaml.DeleteProperty yamlDeleteDescendants =
                descendantPropertyKey == null ? null : new org.openrewrite.yaml.DeleteProperty(descendantPropertyKey, false, true, null);
        DeleteProperty propertiesDeleteDescendants =
                descendantPropertyKey == null ? null : new DeleteProperty(descendantPropertyKey, true);

        return Preconditions.check(new IsPossibleSpringConfigFile(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof Yaml.Documents || sourceFile instanceof Properties.File;
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree t, ExecutionContext ctx) {
                if (t instanceof Yaml.Documents) {
                    Tree deleted = yamlDeleteProperty.getVisitor().visitNonNull(t, ctx);
                    if (yamlDeleteDescendants != null) {
                        deleted = yamlDeleteDescendants.getVisitor().visitNonNull(deleted, ctx);
                    }
                    t = deleted;
                } else if (t instanceof Properties.File) {
                    Tree deleted = propertiesDeleteProperty.getVisitor().visitNonNull(t, ctx);
                    if (propertiesDeleteDescendants != null) {
                        deleted = propertiesDeleteDescendants.getVisitor().visitNonNull(deleted, ctx);
                    }
                    t = deleted;
                }
                return t;
            }
        });
    }

    private static boolean containsGlob(String propertyKey) {
        return propertyKey.indexOf('*') >= 0 || propertyKey.indexOf('?') >= 0 || propertyKey.indexOf('[') >= 0;
    }
}

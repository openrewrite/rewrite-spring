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
package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.properties.DeleteProperty;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

/**
 * A recipe to remove a property (or matching property group) from Spring configuration files. This recipe supports deleting properties from
 * ".properties" and YAML files.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class DeleteSpringProperty extends Recipe {

    @Option(displayName = "Property key",
            description = "The property key to delete. Supports glob expressions",
            example = "management.endpoint.configprops.*")
    String propertyKey;

    @Override
    public String getDisplayName() {
        return "Delete a spring configuration property";
    }

    @Override
    public String getDescription() {
        return "Delete a spring configuration property from any configuration file that contains a matching key.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof Yaml.Documents || sourceFile instanceof Properties.File;
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                //Short circuit visitor navigation for everything except source file
                if (tree instanceof SourceFile) {
                    tree = super.visit(tree, ctx);
                }
                return tree;
            }

            @Override
            public @Nullable Tree preVisit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Yaml.Documents) {
                    doAfterVisit(new org.openrewrite.yaml.DeleteProperty(propertyKey, false, true, null));
                } else if (tree instanceof Properties.File) {
                    doAfterVisit(new DeleteProperty(propertyKey, true, null));
                }
                return tree;
            }
        };
    }
}

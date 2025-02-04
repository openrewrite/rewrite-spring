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
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;
import java.util.regex.Pattern;

/**
 * This composite recipe will change a spring application property key across YAML and properties files.
 * <P>
 * TODO: Add a java visitor to this recipe that will change property keys in @Value, @PropertySource and @TestPropertySource
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeSpringPropertyKey extends Recipe {

    @Override
    public String getDisplayName() {
        return "Change the key of a spring application property";
    }

    @Override
    public String getDescription() {
        return "Change spring application property keys existing in either Properties or Yaml files.";
    }

    @Option(displayName = "Old property key",
            description = "The property key to rename.",
            example = "management.metrics.binders.*.enabled")
    String oldPropertyKey;

    @Option(displayName = "New property key",
            description = "The new name for the property key.",
            example = "management.metrics.enable.process.files")
    String newPropertyKey;

    @Option(displayName = "Except",
            description = "Regex. If any of these property keys exist as direct children of `oldPropertyKey`, then they will not be moved to `newPropertyKey`.",
            required = false,
            example = "jvm")
    @Nullable
    List<String> except;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        org.openrewrite.yaml.ChangePropertyKey yamlChangePropertyKey =
                new org.openrewrite.yaml.ChangePropertyKey(oldPropertyKey, newPropertyKey, true, except, null);
        org.openrewrite.properties.ChangePropertyKey propertiesChangePropertyKey =
                new org.openrewrite.properties.ChangePropertyKey(oldPropertyKey, newPropertyKey, true, false);
        org.openrewrite.properties.ChangePropertyKey subpropertiesChangePropertyKey =
                new org.openrewrite.properties.ChangePropertyKey(Pattern.quote(oldPropertyKey + ".") + exceptRegex() + "(.+)", newPropertyKey + ".$1", true, true);

        return Preconditions.check(new IsPossibleSpringConfigFile(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Yaml.Documents) {
                    tree = yamlChangePropertyKey.getVisitor().visit(tree, ctx);
                } else if (tree instanceof Properties.File) {
                    if (FindProperties.find((Properties.File) tree, newPropertyKey, true).isEmpty()) {
                        Tree newTree = propertiesChangePropertyKey.getVisitor().visit(tree, ctx);
                        // for compatibility with yaml syntax, a spring property key will never have both a (scalar) value and also subproperties
                        if (newTree == tree) {
                            newTree = subpropertiesChangePropertyKey.getVisitor().visit(tree, ctx);
                        }
                        tree = newTree;
                    }
                }
                return tree;
            }
        });
    }

    private String exceptRegex() {
        return except == null || except.isEmpty() ?
                "" :
                "(?!(" + String.join("|", except) + "))";
    }
}

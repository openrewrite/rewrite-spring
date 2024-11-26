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
import org.jetbrains.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

@EqualsAndHashCode(callSuper = false)
@Value
public class CommentOutSpringPropertyKey extends Recipe {

    @Override
    public String getDisplayName() {
        return "Comment out Spring properties";
    }

    @Override
    public String getDescription() {
        return "Add inline comments before specified Spring properties, and comment out the property.";
    }

    @Option(displayName = "Property key",
            description = "The name of the property key whose value is to be changed.",
            example = "management.metrics.binders.files.enabled")
    String propertyKey;

    @Option(displayName = "Comment",
            description = "Comment to replace the property key.",
            example = "This property is deprecated and no longer applicable starting from Spring Boot 3.0.x")
    String comment;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String inlineComment = " # " + comment;
        String regex = "(?<!" + inlineComment + ")$";
        Recipe changeProperties = new org.openrewrite.properties.ChangePropertyValue(propertyKey, inlineComment, regex, true, null);
        Recipe changeYaml = new org.openrewrite.yaml.CommentOutProperty(propertyKey, comment) ;
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Properties.File) {
                    return changeProperties.getVisitor().visit(tree, ctx);
                } else if (tree instanceof Yaml.Documents) {
                    return changeYaml.getVisitor().visit(tree, ctx);
                }
                return tree;
            }
        };
    }
}

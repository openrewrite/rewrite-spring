/*
 * Copyright 2024 the original author or authors.
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

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class InlineCommentSpringProperties extends Recipe {

    @Override
    public String getDisplayName() {
        return "Comment Spring properties";
    }

    @Override
    public String getDescription() {
        return "Add inline comments to specified spring properties.";
    }

    @Option(displayName = "Property keys list",
            description = "The list of names of the property keys to comment.",
            example = "management.metrics.binders.files.enabled")
    List<String> propertyKeys;

    @Option(displayName = "Inline comment",
            description = "Inline comment to be inserted",
            example = "This property is deprecated and no longer applicable starting from Spring Boot 3.0.x")
    String comment;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String inlineComment = " # " + comment;
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                Tree processingTree = tree;
                for (String key : propertyKeys) {
                    String regex = "(?<!" + inlineComment + ")$";
                    ChangeSpringPropertyValue changeSpringPropertyValue = new ChangeSpringPropertyValue(key, inlineComment, regex, true, null);
                    processingTree = changeSpringPropertyValue.getVisitor().visit(processingTree, ctx);
                }
                return processingTree;
            }
        };
    }
}

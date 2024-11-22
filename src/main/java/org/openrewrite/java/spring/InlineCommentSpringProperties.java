package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.*;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class InlineCommentSpringProperties extends Recipe {

    @Override
    public @NotNull @NlsRewrite.DisplayName String getDisplayName() {
        return "Comment spring properties";
    }

    @Override
    public @NotNull @NlsRewrite.Description String getDescription() {
        return "Add inline comments to specified spring properties.";
    }

    @Option(displayName = "Property keys list",
            description = "The list of names of the property keys to comment.",
            example = "management.metrics.binders.files.enabled")
    List<String> propertyKeys;

    @Option(displayName = "Inline comment",
            description = "Inline comment to be inserted",
            example = "this property is deprecated and no longer applicable starting from Spring Boot 3.0.x")
    String comment;

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {
        String inlineComment = " # " + comment;
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, @NotNull ExecutionContext ctx) {
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

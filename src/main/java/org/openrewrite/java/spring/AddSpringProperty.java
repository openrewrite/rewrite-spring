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
import org.openrewrite.properties.AddProperty;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A recipe to uniformly add a property to Spring configuration file. This recipe supports adding properties to
 * ".properties" and YAML files. This recipe will only add the property if it does not already exist within the
 * configuration file.
 * <P>
 * NOTE: Because an application may have a large collection of yaml files (some of which may not even be related to
 *       Spring configuration), this recipe will only make changes to files that match one of the pathExpressions. If
 *       the recipe is configured without pathExpressions, it will query the execution context for reasonable defaults.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class AddSpringProperty extends Recipe {

    @Option(displayName = "Property key",
            description = "The property key to add.",
            example = "management.metrics.enable.process.files")
    String property;

    @Option(displayName = "Property value",
            description = "The value of the new property key.",
            example = "true")
    String value;

    @Option(displayName = "Optional comment to be prepended to the property",
            description = "A comment that will be added to the new property.",
            required = false,
            example = "This is a comment")
    @Nullable
    String comment;

    @Option(displayName = "Optional list of file path matcher",
            description = "Each value in this list represents a glob expression that is used to match which files will " +
                          "be modified. If this value is not present, this recipe will query the execution context for " +
                          "reasonable defaults. (\"**/application.yml\", \"**/application.yml\", and \"**/application.properties\".",
            required = false,
            example = "**/application.yml")
    @Nullable
    List<String> pathExpressions;

    @Override
    public String getDisplayName() {
        return "Add a spring configuration property";
    }

    @Override
    public String getDescription() {
        return "Add a spring configuration property to a configuration file if it does not already exist in that file.";
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
                if (tree instanceof Yaml.Documents && sourcePathMatches(((SourceFile)tree).getSourcePath(), ctx)) {
                    doAfterVisit(createMergeYamlVisitor());
                } else if (tree instanceof Properties.File && sourcePathMatches(((SourceFile)tree).getSourcePath(), ctx)) {
                    doAfterVisit(new AddProperty(property, value, null));
                }
                return tree;
            }
        };
    }

    private boolean sourcePathMatches(Path sourcePath, ExecutionContext ctx) {
        List<String> expressions = pathExpressions;
        if (expressions == null || pathExpressions.isEmpty()) {
            //If not defined, get reasonable defaults from the execution context.
            expressions = SpringExecutionContextView.view(ctx).getDefaultApplicationConfigurationPaths();
        }
        if (expressions.isEmpty()) {
            return true;
        }
        for (String filePattern : expressions) {
            if (filePattern.startsWith("**")) {
                sourcePath = Paths.get(".").resolve(sourcePath.normalize());
            } else {
                sourcePath = sourcePath.normalize();
            }

            PathMatcher pathMatcher = sourcePath.getFileSystem().getPathMatcher("glob:" + filePattern);
            if (pathMatcher.matches(sourcePath)) {
                return true;
            }
        }

        return false;
    }

    private MergeYaml createMergeYamlVisitor() {
        String[] propertyParts = property.split("\\.");

        StringBuilder yaml = new StringBuilder();

        String indent = "";
        for (String part : propertyParts) {
            if (yaml.length() > 0) {
                yaml.append("\n");
            }
            if (comment != null) {
                //noinspection StringEquality
                if (part == propertyParts[propertyParts.length - 1]) {
                    yaml.append(indent).append("# ").append(comment).append("\n");
                }
            }
            yaml.append(indent).append(part).append(":");
            indent = indent + "  ";
        }
        if (quoteValue(value)) {
            yaml.append(" \"").append(value).append('"');
        } else {
            yaml.append(" ").append(value);
        }
        return new MergeYaml("$", yaml.toString(), true, null, null);
    }

    private static final Pattern scalarNeedsAQuote = Pattern.compile("[^a-zA-Z\\d\\s]*");
    private boolean quoteValue(String value) {
        return scalarNeedsAQuote.matcher(value).matches();
    }
}

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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.properties.AddProperty;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;

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
                          "be modified",
            required = false,
            example = "**/application-*.properties")
    @Nullable
    List<String> pathExpressions;

    @Override
    public String getDisplayName() {
        return "Add a spring configuration property to a configuration file if it does not already exist in that file.";
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {

        MergeYaml mergeYaml = createMergeYamlVisitor();
        AddProperty addProperty = new AddProperty(property, value, null);
        return ListUtils.map(before, s -> {
            if (s instanceof Yaml.Documents && sourcePathMatches(s.getSourcePath())) {
                s = (Yaml.Documents) mergeYaml.getVisitor().visit(s, ctx);
            } else if (s instanceof Properties.File && sourcePathMatches(s.getSourcePath())) {
                s = (Properties.File) addProperty.getVisitor().visit(s, ctx);
            }
            return s;
        });
    }

    private boolean sourcePathMatches(Path sourcePath) {
        if (pathExpressions == null || pathExpressions.isEmpty()) {
            return true;
        }
        for (String filePattern : pathExpressions) {
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
        yaml.append(" ").append(value);
        return new MergeYaml("$", yaml.toString(), true, null, null);
    }

}

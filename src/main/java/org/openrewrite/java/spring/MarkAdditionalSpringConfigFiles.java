/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.marker.SourceSet;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Collections;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class MarkAdditionalSpringConfigFiles extends Recipe {

    @Option(displayName = "File path patterns",
            description = "Glob patterns (relative to the repository root) identifying YAML or properties files " +
                    "that should be treated as Spring configuration even though they live outside a standard " +
                    "`src/main/resources` source set. Files already carrying a `SourceSet` marker are left alone.",
            example = "**/properties/*.properties")
    List<String> pathPatterns;

    @Override
    public String getDisplayName() {
        return "Mark additional files as Spring configuration";
    }

    @Override
    public String getDescription() {
        return "Attach a `SpringConfigFile` marker to YAML/properties files matching the provided glob patterns " +
                "so that Spring property recipes such as `ChangeSpringPropertyKey`, `DeleteSpringProperty`, " +
                "`ChangeSpringPropertyValue`, and `CommentOutSpringPropertyKey` will visit files that live " +
                "outside standard resource source sets. Files that already pass the `SourceSet`-based check are " +
                "skipped to avoid redundant markers.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Properties.File) && !(tree instanceof Yaml.Documents)) {
                    return tree;
                }
                SourceFile sf = (SourceFile) tree;
                if (sf.getMarkers().findFirst(SourceSet.class).isPresent() ||
                        sf.getMarkers().findFirst(SpringConfigFile.class).isPresent()) {
                    return tree;
                }
                for (String pattern : pathPatterns == null ? Collections.<String>emptyList() : pathPatterns) {
                    if (PathUtils.matchesGlob(sf.getSourcePath(), pattern)) {
                        return sf.withMarkers(sf.getMarkers().add(new SpringConfigFile(Tree.randomId())));
                    }
                }
                return tree;
            }
        };
    }
}

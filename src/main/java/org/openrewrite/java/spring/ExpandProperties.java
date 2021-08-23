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

import org.openrewrite.ExecutionContext;
import org.openrewrite.HasSourcePath;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class ExpandProperties extends Recipe {
    @Override
    public String getDisplayName() {
        return "Expand Spring YAML properties";
    }

    @Override
    public String getDescription() {
        return "Expand YAML properties to not use the dot syntax shortcut.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new HasSourcePath<>("**/application*.yml");
    }

    @Override
    public YamlVisitor<ExecutionContext> getVisitor() {
        return new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = entry;
                String key = e.getKey().getValue();
                if (key.contains(".")) {
                    e = e.withKey(e.getKey().withValue(key.substring(0, key.indexOf('.'))));
                    e = e.withValue(new Yaml.Mapping(
                            randomId(),
                            Markers.EMPTY,
                            singletonList(
                                    new Yaml.Mapping.Entry(
                                            randomId(),
                                            "",
                                            Markers.EMPTY,
                                            new Yaml.Scalar(
                                                    randomId(),
                                                    "",
                                                    Markers.EMPTY,
                                                    Yaml.Scalar.Style.PLAIN,
                                                    key.substring(key.indexOf('.') + 1)),
                                            "",
                                            e.getValue()
                                    )
                            )
                    ));
                    e = autoFormat(e, ctx, getCursor().getParentOrThrow());
                }
                return super.visitMappingEntry(e, ctx);
            }
        };
    }
}

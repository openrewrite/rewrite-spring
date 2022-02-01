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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext executionContext) {
                Yaml docs = super.visitDocuments(documents, executionContext);
                Yaml docsExpanded = new ExpandEntriesVisitor().visitNonNull(docs, executionContext);
                if (docsExpanded != docs) {
                    docs = new CoalesceEntriesVisitor().visitNonNull(docsExpanded, executionContext);
                }
                return docs;
            }
        };
    }

    private static class ExpandEntriesVisitor extends YamlVisitor<ExecutionContext> {
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
                                                null,
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
    }

    private static class CoalesceEntriesVisitor extends YamlVisitor<ExecutionContext> {
        @Override
        public Yaml visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
            Yaml.Mapping m = mapping;
            Map<String, List<Yaml.Mapping.Entry>> entriesByKey = new HashMap<>();
            for (Yaml.Mapping.Entry entry : m.getEntries()) {
                if (entry.getValue() instanceof Yaml.Mapping) {
                    entriesByKey.computeIfAbsent(entry.getKey().getValue(), v -> new ArrayList<>()).addAll(((Yaml.Mapping) entry.getValue()).getEntries());
                }
            }
            for (Map.Entry<String, List<Yaml.Mapping.Entry>> entries : entriesByKey.entrySet()) {
                if (entries.getValue().size() > 1) {
                    Yaml.Mapping newMapping = new Yaml.Mapping(
                            randomId(),
                            Markers.EMPTY,
                            entries.getValue()
                    );
                    Yaml.Mapping.Entry newEntry = new Yaml.Mapping.Entry(randomId(),
                            "",
                            Markers.EMPTY,
                            new Yaml.Scalar(randomId(), "", Markers.EMPTY, Yaml.Scalar.Style.PLAIN, null, entries.getKey()),
                            "",
                            newMapping);

                    m = m.withEntries(ListUtils.map(mapping.getEntries(), (i, ent) -> {
                        if (ent.getKey().getValue().equals(entries.getKey())) {
                            return null;
                        }
                        return ent;
                    }));
                    m = m.withEntries(ListUtils.concat(m.getEntries(), newEntry));
                    m = autoFormat(m, ctx);
                }
            }
            return super.visitMapping(m, ctx);
        }
    }
}


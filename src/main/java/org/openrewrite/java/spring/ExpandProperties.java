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

import com.fasterxml.jackson.annotation.JsonCreator;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Markers;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class ExpandProperties extends Recipe {

    @Option(displayName = "Source file mask",
            description = "An optional source file path mask use to restrict which YAML files will be expanded by this recipe.",
            example = "**/application*.yml",
            required = false)
    @Nullable
    private final String sourceFileMask;

    @Override
    public String getDisplayName() {
        return "Expand Spring YAML properties";
    }

    @Override
    public String getDescription() {
        return "Expand YAML properties to not use the dot syntax shortcut.";
    }

    public ExpandProperties() {
        this.sourceFileMask = null;
    }

    @JsonCreator
    public ExpandProperties(String sourceFileMask) {
        this.sourceFileMask = sourceFileMask;
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        if (sourceFileMask != null) {
            return new HasSourcePath<>(sourceFileMask);
        }
        return null;
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
            if (key.contains(".") && e.getKey() instanceof Yaml.Scalar) {
                e = e.withKey(((Yaml.Scalar)e.getKey()).withValue(key.substring(0, key.indexOf('.'))));
                e = e.withValue(new Yaml.Mapping(
                        randomId(),
                        Markers.EMPTY,
                        null,
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
                        ),
                        null,
                        null
                ));
                e = autoFormat(e, ctx, getCursor().getParentOrThrow());
            }
            return super.visitMappingEntry(e, ctx);
        }
    }

    private static class CoalesceEntriesVisitor extends YamlVisitor<ExecutionContext> {
        @Override
        public Yaml visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
            Map<String, List<Yaml.Mapping>> mappingsByKey = new HashMap<>();
            for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
                if (entry.getValue() instanceof Yaml.Mapping) {
                    mappingsByKey.computeIfAbsent(entry.getKey().getValue(), v -> new ArrayList<>()).add((Yaml.Mapping)entry.getValue());
                }
            }

            for (Map.Entry<String, List<Yaml.Mapping>> keyMappings : mappingsByKey.entrySet()) {
                if (keyMappings.getValue().size() > 1) {
                    Yaml.Mapping newMapping = new Yaml.Mapping(
                            randomId(),
                            Markers.EMPTY,
                            null,
                            keyMappings.getValue().stream().flatMap(duplicateMapping -> duplicateMapping.getEntries().stream())
                                    .collect(Collectors.toList()),
                            null,
                            null
                    );
                    Yaml.Mapping.Entry newEntry = new Yaml.Mapping.Entry(randomId(),
                            "",
                            Markers.EMPTY,
                            new Yaml.Scalar(randomId(), "", Markers.EMPTY, Yaml.Scalar.Style.PLAIN, null, keyMappings.getKey()),
                            "", newMapping);

                    AtomicInteger insertIndex = new AtomicInteger(-1);
                    mapping = mapping.withEntries(ListUtils.map(mapping.getEntries(), (i, ent) -> {
                        if (ent.getKey().getValue().equals(keyMappings.getKey())) {
                            if (insertIndex.get() < 0) {
                                insertIndex.set(i);
                            }
                            return null;
                        }
                        return ent;
                    }));
                    //noinspection ConstantConditions
                    mapping = maybeAutoFormat(mapping, mapping.withEntries(ListUtils.insertAll(mapping.getEntries(), insertIndex.get(), Collections.singletonList(newEntry))),
                            ctx, getCursor().getParent().getValue() instanceof Yaml.Document ? getCursor().getParent() : getCursor());
                }
            }
            return super.visitMapping(mapping, ctx);
        }
    }
}


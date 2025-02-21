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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class ExpandProperties extends Recipe {

    @Option(displayName = "Source file mask",
            description = "An optional source file path mask use to restrict which YAML files will be expanded by this recipe.",
            example = "**/application*.yml",
            required = false)
    @Nullable
    private String sourceFileMask;

    @Override
    public String getDisplayName() {
        return "Expand Spring YAML properties";
    }

    @Override
    public String getDescription() {
        return "Expand YAML properties to not use the dot syntax shortcut.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        YamlVisitor<ExecutionContext> visitor = new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                Yaml.Documents docs = (Yaml.Documents) super.visitDocuments(documents, ctx);
                Yaml.Documents docsExpanded = (Yaml.Documents) new ExpandEntriesVisitor().visitNonNull(docs, ctx);
                if (docsExpanded != docs) {
                    docs = (Yaml.Documents) new CoalesceEntriesVisitor().visitNonNull(docsExpanded, ctx);
                    docs = removeEmptyFirstLine(docs, ctx);
                }
                return docs;
            }

            // If the old first entry was coalesced under a subsequent entry then it will look like a newline was added
            private Yaml.Documents removeEmptyFirstLine(Yaml.Documents docs, ExecutionContext ctx) {
                return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
                    boolean doneTrimming;
                    @Override
                    public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                        doneTrimming = true;
                        return scalar.withPrefix(trimNewlineBeforeComment(scalar.getPrefix()));
                    }

                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        doneTrimming = true;
                        return entry.withPrefix(trimNewlineBeforeComment(entry.getPrefix()));
                    }

                    @Override
                    public Yaml.Sequence.Entry visitSequenceEntry(Yaml.Sequence.Entry entry, ExecutionContext ctx) {
                        doneTrimming = true;
                        return entry.withPrefix(trimNewlineBeforeComment(entry.getPrefix()));
                    }

                    @Override
                    public @Nullable Yaml visit(@Nullable Tree tree, ExecutionContext ctx) {
                        if (doneTrimming) {
                            return (Yaml) tree;
                        }
                        return super.visit(tree, ctx);
                    }

                    private String trimNewlineBeforeComment(String prefix) {
                        int hashIndex = prefix.indexOf('#');
                        if(hashIndex >= 0) {
                            return prefix.substring(0, hashIndex).trim() + prefix.substring(hashIndex);
                        }
                        return prefix.trim();
                    }
                }.visitNonNull(docs, ctx);
            }
        };
        return sourceFileMask != null ?
                Preconditions.check(new FindSourceFiles(sourceFileMask), visitor) :
                visitor;
    }

    private static class ExpandEntriesVisitor extends YamlVisitor<ExecutionContext> {
        @Override
        public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry e = entry;
            String key = e.getKey().getValue();
            if (key.contains(".") && e.getKey() instanceof Yaml.Scalar) {
                e = e.withKey(((Yaml.Scalar) e.getKey()).withValue(key.substring(0, key.indexOf('.'))));
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
                                                null,
                                                key.substring(key.indexOf('.') + 1)),
                                        "",
                                        e.getValue()
                                )
                        ),
                        null,
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
                    mappingsByKey.computeIfAbsent(entry.getKey().getValue(), v -> new ArrayList<>()).add((Yaml.Mapping) entry.getValue());
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
                            null,
                            null
                    );
                    Yaml.Mapping.Entry newEntry = autoFormat(
                            new Yaml.Mapping.Entry(randomId(),
                                    "",
                                    Markers.EMPTY,
                                    new Yaml.Scalar(randomId(), "", Markers.EMPTY, Yaml.Scalar.Style.PLAIN, null, null, keyMappings.getKey()),
                                    "", newMapping),
                            ctx, getCursor());

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
                    mapping = mapping.withEntries(ListUtils.insertAll(mapping.getEntries(), insertIndex.get(), Collections.singletonList(newEntry)));
                }
            }
            return super.visitMapping(mapping, ctx);
        }
    }
}

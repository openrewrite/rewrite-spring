/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.spring.IsPossibleSpringConfigFile;
import org.openrewrite.java.spring.SpringConfigFiles;
import org.openrewrite.properties.AddProperty;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateArtemisProperties extends ScanningRecipe<SpringConfigFiles> {

    String displayName = "Migrate `spring.artemis.host` and `spring.artemis.port` to `spring.artemis.broker-url`";

    String description = "Combines `spring.artemis.host` and `spring.artemis.port` into `spring.artemis.broker-url` " +
            "in the format `tcp://host:port`.";

    @Override
    public SpringConfigFiles getInitialValue(ExecutionContext ctx) {
        return new SpringConfigFiles();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(SpringConfigFiles acc) {
        return acc.getScanner();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(SpringConfigFiles acc) {
        return Preconditions.check(new IsPossibleSpringConfigFile(acc), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Properties.File) {
                    return migrateProperties((Properties.File) tree, ctx);
                } else if (tree instanceof Yaml.Documents) {
                    return migrateYaml((Yaml.Documents) tree, ctx);
                }
                return tree;
            }

            private Tree migrateProperties(Properties.File file, ExecutionContext ctx) {
                Set<Properties.Entry> hostEntries = FindProperties.find(file, "spring.artemis.host", true);
                Set<Properties.Entry> portEntries = FindProperties.find(file, "spring.artemis.port", true);
                if (hostEntries.isEmpty() && portEntries.isEmpty()) {
                    return file;
                }
                String host = hostEntries.isEmpty() ? "localhost" :
                        hostEntries.iterator().next().getValue().getText();
                String port = portEntries.isEmpty() ? "61616" :
                        portEntries.iterator().next().getValue().getText();
                String brokerUrl = "tcp://" + host + ":" + port;
                if (!hostEntries.isEmpty()) {
                    file = (Properties.File) new org.openrewrite.properties.DeleteProperty("spring.artemis.host", true)
                            .getVisitor().visitNonNull(file, ctx);
                }
                if (!portEntries.isEmpty()) {
                    file = (Properties.File) new org.openrewrite.properties.DeleteProperty("spring.artemis.port", true)
                            .getVisitor().visitNonNull(file, ctx);
                }
                return new AddProperty("spring.artemis.broker-url", brokerUrl, null, null, null, null, null)
                        .getVisitor().visitNonNull(file, ctx);
            }

            private Tree migrateYaml(Yaml.Documents documents, ExecutionContext ctx) {
                List<Yaml.Document> migratedDocuments = new ArrayList<>(documents.getDocuments().size());
                // Documents later in a multi-document YAML override earlier ones, so a document that sets only
                // one of host or port keeps using the other value established by the documents before it.
                String effectiveHost = null;
                String effectivePort = null;
                boolean changed = false;
                for (Yaml.Document document : documents.getDocuments()) {
                    if (!(document.getBlock() instanceof Yaml.Mapping)) {
                        migratedDocuments.add(document);
                        continue;
                    }
                    Set<Yaml.Block> hostBlocks = FindProperty.find(document, "spring.artemis.host", true);
                    Set<Yaml.Block> portBlocks = FindProperty.find(document, "spring.artemis.port", true);
                    String host = scalarValue(hostBlocks);
                    String port = scalarValue(portBlocks);
                    if (host == null && port == null) {
                        migratedDocuments.add(document);
                        continue;
                    }
                    effectiveHost = host != null ? host : effectiveHost;
                    effectivePort = port != null ? port : effectivePort;
                    String brokerUrl = "tcp://" + (effectiveHost == null ? "localhost" : effectiveHost) +
                            ":" + (effectivePort == null ? "61616" : effectivePort);

                    // Scope the merge and the deletes to this one document, so that each document keeps its own
                    // broker URL rather than every document ending up with the first document's host and port.
                    Yaml.Documents singleDocument = documents.withDocuments(singletonList(document));
                    singleDocument = (Yaml.Documents) new MergeYaml("$.spring.artemis", "broker-url: " + brokerUrl, true, null, null, null, null, null)
                            .getVisitor().visitNonNull(singleDocument, ctx);
                    if (!hostBlocks.isEmpty()) {
                        singleDocument = (Yaml.Documents) new org.openrewrite.yaml.DeleteProperty("spring.artemis.host", false, true, null)
                                .getVisitor().visitNonNull(singleDocument, ctx);
                    }
                    if (!portBlocks.isEmpty()) {
                        singleDocument = (Yaml.Documents) new org.openrewrite.yaml.DeleteProperty("spring.artemis.port", false, true, null)
                                .getVisitor().visitNonNull(singleDocument, ctx);
                    }
                    migratedDocuments.add(singleDocument.getDocuments().get(0));
                    changed = true;
                }
                return changed ? documents.withDocuments(migratedDocuments) : documents;
            }

            private @Nullable String scalarValue(Set<Yaml.Block> blocks) {
                for (Yaml.Block block : blocks) {
                    if (block instanceof Yaml.Scalar) {
                        return ((Yaml.Scalar) block).getValue();
                    }
                }
                return null;
            }
        });
    }
}

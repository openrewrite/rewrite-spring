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
import org.openrewrite.properties.AddProperty;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateArtemisProperties extends Recipe {

    String displayName = "Migrate `spring.artemis.host` and `spring.artemis.port` to `spring.artemis.broker-url`";

    String description = "Combines `spring.artemis.host` and `spring.artemis.port` into `spring.artemis.broker-url` " +
            "in the format `tcp://host:port`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsPossibleSpringConfigFile(), new TreeVisitor<Tree, ExecutionContext>() {
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
                return new AddProperty("spring.artemis.broker-url", brokerUrl, null, null, null)
                        .getVisitor().visitNonNull(file, ctx);
            }

            private Tree migrateYaml(Yaml.Documents documents, ExecutionContext ctx) {
                Set<Yaml.Block> hostBlocks = FindProperty.find(documents, "spring.artemis.host", true);
                Set<Yaml.Block> portBlocks = FindProperty.find(documents, "spring.artemis.port", true);
                if (hostBlocks.isEmpty() && portBlocks.isEmpty()) {
                    return documents;
                }
                String host = "localhost";
                String port = "61616";
                if (!hostBlocks.isEmpty() && hostBlocks.iterator().next() instanceof Yaml.Scalar) {
                    host = ((Yaml.Scalar) hostBlocks.iterator().next()).getValue();
                }
                if (!portBlocks.isEmpty() && portBlocks.iterator().next() instanceof Yaml.Scalar) {
                    port = ((Yaml.Scalar) portBlocks.iterator().next()).getValue();
                }
                String brokerUrl = "tcp://" + host + ":" + port;
                documents = (Yaml.Documents) new MergeYaml("$.spring.artemis", "broker-url: " + brokerUrl, true, null, null, null, null, null)
                        .getVisitor().visitNonNull(documents, ctx);
                if (!hostBlocks.isEmpty()) {
                    documents = (Yaml.Documents) new org.openrewrite.yaml.DeleteProperty("spring.artemis.host", false, true, null)
                            .getVisitor().visitNonNull(documents, ctx);
                }
                if (!portBlocks.isEmpty()) {
                    documents = (Yaml.Documents) new org.openrewrite.yaml.DeleteProperty("spring.artemis.port", false, true, null)
                            .getVisitor().visitNonNull(documents, ctx);
                }
                return documents;
            }
        });
    }
}

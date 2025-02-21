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
package org.openrewrite.java.spring.boot2;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.spring.ExpandProperties;
import org.openrewrite.yaml.CoalescePropertiesVisitor;
import org.openrewrite.yaml.MergeYamlVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MergeBootstrapYamlWithApplicationYaml extends ScanningRecipe<MergeBootstrapYamlWithApplicationYaml.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Merge Spring `bootstrap.yml` with `application.yml`";
    }

    @Override
    public String getDescription() {
        return "In Spring Boot 2.4, support for `bootstrap.yml` was removed. It's properties should be merged with `application.yml`.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile source = (SourceFile) tree;
                Path sourcePath = source.getSourcePath();
                if (acc.getBootstrapYaml() == null && source instanceof Yaml.Documents &&
                        PathUtils.matchesGlob(sourcePath, "**/main/resources/bootstrap.yml")) {
                    acc.setBootstrapYaml((Yaml.Documents) source);
                } else if (acc.getApplicationYaml() == null &&
                        PathUtils.matchesGlob(sourcePath, "**/main/resources/application.yml")) {
                    acc.setApplicationYaml((Yaml.Documents) source);
                }
                return source;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (acc.getBootstrapYaml() != null && acc.getApplicationYaml() == null) {
            // rename
            Yaml.Documents yaml = acc.getBootstrapYaml();
            return Collections.singletonList(yaml.withSourcePath(yaml.getSourcePath().resolve("application.yml")));
        }
        return Collections.emptyList();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.getBootstrapYaml() == null) {
            return TreeVisitor.noop();
        }

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile source = (SourceFile) tree;
                Path sourcePath = source.getSourcePath();
                if (sourcePath.equals(acc.getBootstrapYaml().getSourcePath())) {
                    // delete bootstrap.yml file
                    source = null;
                } else if (acc.getApplicationYaml() != null && sourcePath.equals(acc.getApplicationYaml().getSourcePath())) {
                    // update application.yml file
                    AtomicBoolean merged = new AtomicBoolean(false);

                    Yaml.Documents a = (Yaml.Documents) new ExpandProperties(null).getVisitor().visit(acc.getApplicationYaml(), ctx);
                    Yaml.Documents b = (Yaml.Documents) new ExpandProperties(null).getVisitor().visit(acc.getBootstrapYaml(), ctx);
                    assert a != null;
                    assert b != null;

                    //noinspection unchecked
                    source = new CoalescePropertiesVisitor<Integer>().visitDocuments(a.withDocuments(ListUtils.map((List<Yaml.Document>) a.getDocuments(), doc -> {
                        if (merged.compareAndSet(false, true) && FindProperty.find(doc, "spring.config.activate.on-profile", true).isEmpty()) {
                            return (Yaml.Document) new MergeYamlVisitor<Integer>(doc.getBlock(), b.getDocuments()
                                    .get(0).getBlock(), true, null, null, null).visit(doc, 0, new Cursor(new Cursor(null, a), doc));
                        }
                        return doc;
                    })), 0);
                }
                return source;
            }
        };
    }

    @Data
    static class Accumulator {
        Yaml.@Nullable Documents bootstrapYaml;

        Yaml.@Nullable Documents applicationYaml;
    }
}

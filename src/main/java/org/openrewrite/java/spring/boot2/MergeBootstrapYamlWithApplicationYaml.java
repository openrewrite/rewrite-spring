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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.spring.ExpandProperties;
import org.openrewrite.yaml.CoalescePropertiesVisitor;
import org.openrewrite.yaml.MergeYamlVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MergeBootstrapYamlWithApplicationYaml extends Recipe {
    @Override
    public String getDisplayName() {
        return "Merge Spring `bootstrap.yml` with `application.yml`";
    }

    @Override
    public String getDescription() {
        return "In Spring Boot 2.4, support for `bootstrap.yml` was removed. It's properties should be merged with `application.yml`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new HasSourcePath<>("**/main/resources/bootstrap.yml");
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        Yaml.Documents bootstrapYaml = findByPath(before, "bootstrap.yml");
        Yaml.Documents applicationYaml = findByPath(before, "application.yml");

        assert bootstrapYaml != null;
        return ListUtils.map(before, source -> {
            if (source == bootstrapYaml) {
                if (applicationYaml == null) {
                    return source.withSourcePath(source.getSourcePath().resolveSibling("application.yml"));
                }
                return null;
            } else if (applicationYaml != null && source == applicationYaml) {
                AtomicBoolean merged = new AtomicBoolean(false);

                Yaml.Documents a = (Yaml.Documents) new ExpandProperties().getVisitor().visit(applicationYaml, ctx);
                assert a != null;

                Yaml.Documents b = (Yaml.Documents) new ExpandProperties().getVisitor().visit(bootstrapYaml, ctx);
                assert b != null;

                //noinspection unchecked
                return (SourceFile) new CoalescePropertiesVisitor<Integer>().visit(a.withDocuments(ListUtils.map((List<Yaml.Document>) a.getDocuments(), doc -> {
                    if (merged.compareAndSet(false, true) && FindProperty.find(doc, "spring.config.activate.on-profile", true).isEmpty()) {
                        return (Yaml.Document) new MergeYamlVisitor<Integer>(doc.getBlock(), b.getDocuments()
                                .get(0).getBlock(), true, null).visit(doc, 0, new Cursor(new Cursor(null, a), doc));
                    }
                    return doc;
                })), 0);
            }

            return source;
        });
    }

    @Nullable
    private Yaml.Documents findByPath(List<SourceFile> before, String fileName) {
        for (SourceFile sourceFile : before) {
            Path sourcePath = sourceFile.getSourcePath();
            PathMatcher pathMatcher = sourcePath.getFileSystem().getPathMatcher("glob:**/main/resources/" + fileName);
            if (pathMatcher.matches(sourcePath)) {
                return (Yaml.Documents) sourceFile;
            }
        }
        return null;
    }
}

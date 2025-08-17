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
import org.intellij.lang.annotations.Language;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.CoalesceProperties;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

@EqualsAndHashCode(callSuper = false)
@Value
class MigrateDatabaseCredentialsForToolYaml extends Recipe {
    @Language("markdown")
    String tool;

    @Override
    public String getDisplayName() {
        return "Migrate " + tool + " credentials";
    }

    @Override
    public String getDescription() {
        return "Migrate " + tool + " credentials.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                if (FindProperty.find(documents, "spring." + tool + ".username", true).isEmpty() &&
                        FindProperty.find(documents, "spring." + tool + ".password", true).isEmpty()) {
                    doAfterVisit(new FindProperty("spring." + tool + ".url", true, null).getVisitor());
                }
                return documents;
            }
        }, new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                doAfterVisit(new MergeYaml("$.spring." + tool, "username: ${spring.datasource.username}", true, null, null, null, null, null).getVisitor());
                doAfterVisit(new MergeYaml("$.spring." + tool, "password: ${spring.datasource.password}", true, null, null, null, null, null).getVisitor());
                doAfterVisit(new CoalesceProperties().getVisitor());
                return documents;
            }
        });
    }
}

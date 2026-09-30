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
package org.openrewrite.java.flyway;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.ChangeDependencyScope;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.tree.Xml;

@Value
@EqualsAndHashCode(callSuper = false)
public class AlignFlywayModuleScopeWithFlywayCore extends Recipe {

    @Option(displayName = "Flyway module artifactId",
            description = "ArtifactId of the Flyway database module to align with `flyway-core`.",
            example = "flyway-database-postgresql")
    String artifactId;

    String displayName = "Align Flyway module scope with flyway-core";

    String description = "Ensures Flyway database modules keep the same declared Maven scope as `flyway-core` " +
            "when migrations add or touch those dependencies.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document d = super.visitDocument(document, ctx);
                String flywayCoreScope = findDeclaredScope(d.getRoot(), "org.flywaydb", "flyway-core");
                if (!"test".equals(flywayCoreScope)) {
                    return d;
                }
                return (Xml.Document) new ChangeDependencyScope("org.flywaydb", artifactId, "test").getVisitor().visitNonNull(d, ctx);
            }

            private @Nullable String findDeclaredScope(Xml.Tag tag, String groupId, String artifactId) {
                if (isDependency(groupId, artifactId, tag)) {
                    return tag.getChildValue("scope").orElse(null);
                }
                java.util.List<?> content = tag.getContent();
                if (content == null) {
                    return null;
                }
                for (Object child : content) {
                    if (child instanceof Xml.Tag) {
                        String scope = findDeclaredScope((Xml.Tag) child, groupId, artifactId);
                        if (scope != null || isDependency(groupId, artifactId, (Xml.Tag) child)) {
                            return scope;
                        }
                    }
                }
                return null;
            }

            private boolean isDependency(String groupId, String artifactId, Xml.Tag tag) {
                return "dependency".equals(tag.getName()) &&
                        groupId.equals(tag.getChildValue("groupId").orElse(null)) &&
                        artifactId.equals(tag.getChildValue("artifactId").orElse(null));
            }
        };
    }
}

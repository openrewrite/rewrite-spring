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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.AddDependencyVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.tree.Xml;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddFlywayModuleWithScope extends Recipe {
    @Option(displayName = "Module artifact ID",
            description = "The Flyway database module to add.",
            example = "flyway-database-postgresql")
    String artifactId;

    String displayName = "Add Flyway database module with matching scope";
    String description = "Add the Flyway database module using the scope of the direct flyway-core Maven dependency.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Tag dependencies = document.getRoot().getChild("dependencies").orElse(null);
                if (dependencies == null) {
                    return document;
                }

                String scope = null;
                boolean hasCore = false;
                for (Xml.Tag dependency : dependencies.getChildren("dependency")) {
                    String groupId = dependency.getChildValue("groupId").orElse("");
                    String id = dependency.getChildValue("artifactId").orElse("");
                    if ("org.flywaydb".equals(groupId) && artifactId.equals(id)) {
                        // Never rewrite the scope of an existing module.
                        return document;
                    }
                    if ("org.flywaydb".equals(groupId) && "flyway-core".equals(id)) {
                        hasCore = true;
                        scope = dependency.getChildValue("scope").orElse(null);
                    }
                }
                if (!hasCore) {
                    return document;
                }
                doAfterVisit(new AddDependencyVisitor("org.flywaydb", artifactId, null,
                        null, scope, null, null, null, null, null));
                return document;
            }
        };
    }
}

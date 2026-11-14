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
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.xml.tree.Xml;

import java.util.stream.Stream;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddFlywayModuleWithScope extends Recipe {
    private static final String FLYWAY_GROUP_ID = "org.flywaydb";

    @Option(displayName = "Module artifact ID",
            description = "The Flyway database module to add.",
            example = "flyway-database-postgresql")
    String artifactId;

    String displayName = "Add Flyway database module with matching scope";
    String description = "Add the Flyway database module to Maven projects, in the narrowest scope that has `flyway-core` on its classpath.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                MavenResolutionResult mrr = getResolutionResult();
                return Stream.of(Scope.Compile, Scope.Runtime, Scope.Test)
                        .filter(scope -> !mrr.findDependencies(FLYWAY_GROUP_ID, "flyway-core", scope).isEmpty())
                        .findFirst()
                        .filter(scope -> mrr.findDependencies(FLYWAY_GROUP_ID, artifactId, scope).isEmpty())
                        .map(scope -> (Xml.Document) new AddDependencyVisitor(FLYWAY_GROUP_ID, artifactId, "latest.release", null,
                                scope.name().toLowerCase(), null, null, null, null, null).visitNonNull(document, ctx))
                        .orElse(document);
            }
        };
    }
}

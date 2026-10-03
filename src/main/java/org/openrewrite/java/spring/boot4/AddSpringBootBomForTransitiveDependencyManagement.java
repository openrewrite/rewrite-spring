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
package org.openrewrite.java.spring.boot4;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;
import org.openrewrite.maven.AddManagedDependency;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.tree.ResolvedManagedDependency;
import org.openrewrite.semver.Semver;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddSpringBootBomForTransitiveDependencyManagement extends Recipe {
    @Option(displayName = "Spring Boot version", description = "A Spring Boot 4 version or selector beginning with `4.`.", example = "4.0.x")
    String newVersion;

    String displayName = "Manage Spring Boot directly when a third-party BOM manages an older version";
    String description = "Import Spring Boot's BOM ahead of third-party BOMs that still manage an older Spring Boot release.";

    @Override
    public Validated<Object> validate() {
        return super.validate().and(Semver.validate(newVersion, null))
                .and(Validated.test("newVersion", "must select a Spring Boot 4 version", newVersion,
                        version -> version != null && version.startsWith("4.")));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                ResolvedManagedDependency managed = getResolutionResult().getPom()
                        .getManagedDependency("org.springframework.boot", "spring-boot", null, null);
                if (managed == null || managed.getRequestedBom() == null ||
                        "org.springframework.boot".equals(managed.getRequestedBom().getGroupId()) ||
                        !managed.getVersion().matches("[123]\\..*") ||
                        getResolutionResult().findDependencies("org.springframework.boot", "*", null).isEmpty()) {
                    return document;
                }

                Xml.Tag root = document.getRoot();
                Xml.Tag parent = root.getChild("parent").orElse(null);
                if (parent != null && "org.springframework.boot".equals(parent.getChildValue("groupId").orElse(null))) {
                    return document;
                }
                Xml.Tag management = root.getChild("dependencyManagement").orElse(null);
                Xml.Tag dependencies = management == null ? null : management.getChild("dependencies").orElse(null);
                // Only override a BOM imported here; inherited management belongs in the parent project.
                if (dependencies == null || dependencies.getChildren().stream().noneMatch(dependency ->
                        "import".equals(dependency.getChildValue("scope").orElse(null)) &&
                        "pom".equals(dependency.getChildValue("type").orElse(null))) ||
                        dependencies.getChildren().stream().anyMatch(this::isBootBom)) {
                    return document;
                }

                AddManagedDependency add = new AddManagedDependency("org.springframework.boot", "spring-boot-dependencies",
                        newVersion, "import", "pom", null, null, true, null, false, null);
                Xml.Document updated = (Xml.Document) add.getVisitor(add.getInitialValue(ctx))
                        .visitNonNull(document, ctx, getCursor().getParentOrThrow());
                if (updated == document) {
                    return document;
                }

                Xml.Tag updatedManagement = updated.getRoot().getChild("dependencyManagement").get();
                Xml.Tag updatedDependencies = updatedManagement.getChild("dependencies").get();
                Xml.Tag bootBom = updatedDependencies.getChildren().stream().filter(this::isBootBom).findFirst().orElse(null);
                if (bootBom == null) {
                    return updated;
                }
                // Maven chooses the first import when two BOMs manage the same coordinate.
                // Move only the new import, leaving existing BOM comments in place.
                List<Content> content = new ArrayList<>(updatedDependencies.getContent());
                content.remove(bootBom);
                content.add(0, bootBom);
                updatedDependencies = updatedDependencies.withContent(content);
                List<Content> managementContent = new ArrayList<>(updatedManagement.getContent());
                for (int i = 0; i < managementContent.size(); i++) {
                    if (managementContent.get(i).getId().equals(updatedDependencies.getId())) {
                        managementContent.set(i, updatedDependencies);
                    }
                }
                updatedManagement = updatedManagement.withContent(managementContent);
                List<Content> rootContent = new ArrayList<>(updated.getRoot().getContent());
                for (int i = 0; i < rootContent.size(); i++) {
                    if (rootContent.get(i).getId().equals(updatedManagement.getId())) {
                        rootContent.set(i, updatedManagement);
                    }
                }
                maybeUpdateModel();
                return updated.withRoot(updated.getRoot().withContent(rootContent));
            }

            private boolean isBootBom(Xml.Tag dependency) {
                return "org.springframework.boot".equals(dependency.getChildValue("groupId").orElse(null)) &&
                        "spring-boot-dependencies".equals(dependency.getChildValue("artifactId").orElse(null));
            }
        };
    }
}

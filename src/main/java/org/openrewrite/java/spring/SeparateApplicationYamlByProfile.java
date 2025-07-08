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

import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.DeleteProperty;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.*;

public class SeparateApplicationYamlByProfile extends ScanningRecipe<SeparateApplicationYamlByProfile.ApplicationProfiles> {

    @Override
    public String getDisplayName() {
        return "Separate application YAML by profile";
    }

    @Override
    public String getDescription() {
        return "The Spring team's recommendation is to separate profile properties into their own YAML files now.";
    }

    @Override
    public ApplicationProfiles getInitialValue(ExecutionContext ctx) {
        return new ApplicationProfiles();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(ApplicationProfiles acc) {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents yaml, ExecutionContext ctx) {
                if (PathUtils.matchesGlob(yaml.getSourcePath(), "**/application.yml")) {
                    Set<Yaml.Documents> profiles = new HashSet<>(yaml.getDocuments().size());

                    //noinspection unchecked
                    Yaml.Documents mainYaml = yaml.withDocuments(ListUtils.map(
                            (List<Yaml.Document>) yaml.getDocuments(),
                            doc -> {
                                String profileName = FindProperty.find(doc, "spring.config.activate.on-profile", true).stream()
                                        .findAny()
                                        .map(profile -> ((Yaml.Scalar) profile).getValue())
                                        .orElse(null);

                                if (profileName != null && profileName.matches("[A-z0-9-]+")) {
                                    Yaml.Document profileDoc = (Yaml.Document) new DeleteProperty("spring.config.activate.on-profile", true, true, null)
                                            .getVisitor().visit(doc, ctx, new Cursor(null, yaml));
                                    assert profileDoc != null;
                                    profiles.add(yaml
                                            .withId(Tree.randomId())
                                            .withDocuments(Collections.singletonList(profileDoc.withExplicit(false)))
                                            .withSourcePath(yaml.getSourcePath().resolveSibling("application-" + profileName + ".yml")));
                                    return null;
                                }

                                return doc;
                            }));

                    if (!profiles.isEmpty()) {
                        acc.getModifiedMainProfileFiles().put(yaml.getSourcePath(), mainYaml);
                        acc.getNewProfileFiles().addAll(profiles);
                    }
                }
                return yaml;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(ApplicationProfiles acc, ExecutionContext ctx) {
        return acc.getNewProfileFiles();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(ApplicationProfiles acc) {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents yaml, ExecutionContext ctx) {
                return acc.getModifiedMainProfileFiles().getOrDefault(yaml.getSourcePath(), yaml);
            }
        };
    }

    @Value
    public static class ApplicationProfiles {
        Map<Path, Yaml.Documents> modifiedMainProfileFiles = new HashMap<>();
        Set<SourceFile> newProfileFiles = new HashSet<>();
    }
}

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

import lombok.Getter;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.DeleteProperty;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.*;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class SeparateApplicationYamlByProfile extends ScanningRecipe<SeparateApplicationYamlByProfile.ApplicationProfiles> {

    @Getter
    final String displayName = "Separate application YAML by profile";

    @Getter
    final String description = "The Spring team's recommendation is to separate profile properties into their own YAML files now.";

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

                    Yaml.Documents mainYaml = yaml.withDocuments(ListUtils.map(
                            yaml.getDocuments(),
                            doc -> {
                                List<String> profileNames = FindProperty.find(doc, "spring.config.activate.on-profile", true).stream()
                                        .findAny()
                                        .map(profile -> {
                                            if (profile instanceof Yaml.Scalar) {
                                                return singletonList(((Yaml.Scalar) profile).getValue());
                                            }
                                            if (profile instanceof Yaml.Sequence) {
                                                return ((Yaml.Sequence) profile).getEntries().stream()
                                                        .map(entry -> ((Yaml.Scalar) entry.getBlock()).getValue())
                                                        .collect(toList());
                                            }
                                            return null;
                                        })
                                        .orElseGet(ArrayList::new);

                                if (!profileNames.isEmpty() && profileNames.stream().allMatch(name -> name.matches("[A-z0-9-]+"))) {
                                    Yaml.Document profileDoc = (Yaml.Document) new DeleteProperty("spring.config.activate.on-profile", true, true, null)
                                            .getVisitor().visit(doc, ctx, new Cursor(null, yaml));
                                    assert profileDoc != null;
                                    profileNames.forEach(profileName -> {
                                        profiles.add(yaml
                                                .withId(Tree.randomId())
                                                .withDocuments(singletonList(profileDoc.withExplicit(false)))
                                                .withSourcePath(yaml.getSourcePath().resolveSibling("application-" + profileName + ".yml")));
                                    });
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

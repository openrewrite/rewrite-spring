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
package org.openrewrite.java.spring;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.DeleteProperty;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SeparateApplicationYamlByProfile extends Recipe {

    @Override
    public String getDisplayName() {
        return "Separate application YAML by profile";
    }

    @Override
    public String getDescription() {
        return "The Spring team's recommendation is to separate profile properties into their own YAML files now.";
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        return ListUtils.flatMap(before, s -> {
            if (s.getSourcePath().getFileSystem().getPathMatcher("glob:application.yml")
                    .matches(s.getSourcePath().getFileName())) {
                Yaml.Documents yaml = (Yaml.Documents) s;

                Map<Yaml.Document, String> profiles = new HashMap<>(yaml.getDocuments().size());

                //noinspection unchecked
                Yaml.Documents mainYaml = yaml.withDocuments(ListUtils.map(
                        (List<Yaml.Document>) yaml.getDocuments(),
                        doc -> {
                            String profileName = FindProperty.find(doc, "spring.config.activate.on-profile", true).stream()
                                    .findAny()
                                    .map(profile -> ((Yaml.Scalar) profile).getValue())
                                    .orElse(null);

                            if (profileName != null && profileName.matches("[A-z0-9-]+")) {
                                profiles.put((Yaml.Document) new DeleteProperty("spring.config.activate.on-profile", true, true, null)
                                        .getVisitor().visit(doc, ctx, new Cursor(null, yaml)), profileName);
                                return null;
                            }

                            return doc;
                        }));

                List<Yaml.Documents> profileYamls = profiles.entrySet().stream()
                        .map(profile -> yaml
                                .withDocuments(Collections.singletonList(profile.getKey().withExplicit(false)))
                                .withSourcePath(yaml.getSourcePath().resolveSibling("application-" + profile.getValue() + ".yml"))
                        )
                        .collect(Collectors.toList());

                return mainYaml.getDocuments().isEmpty() ? profileYamls : ListUtils.concat(mainYaml, profileYamls);
            }

            return s;
        });
    }
}

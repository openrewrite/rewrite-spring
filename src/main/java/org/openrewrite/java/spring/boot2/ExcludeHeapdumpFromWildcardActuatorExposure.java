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
package org.openrewrite.java.spring.boot2;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.spring.IsPossibleSpringConfigFile;
import org.openrewrite.java.spring.SpringConfigFiles;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class ExcludeHeapdumpFromWildcardActuatorExposure extends ScanningRecipe<ExcludeHeapdumpFromWildcardActuatorExposure.Accumulator> {

    private static final String INCLUDE = "management.endpoints.web.exposure.include";
    private static final String EXCLUDE = "management.endpoints.web.exposure.exclude";
    // Also matches the indexed forms like `exclude[0]`, as a plain value replaces a list bound from those
    private static final String INCLUDE_ANY_FORM = INCLUDE + "*";
    private static final String EXCLUDE_ANY_FORM = EXCLUDE + "*";

    String displayName = "Exclude the `heapdump` Actuator endpoint from wildcard web exposure";

    String description = "When `management.endpoints.web.exposure.include` contains `*`, add " +
            "`management.endpoints.web.exposure.exclude=heapdump` so that heap dumps, which can contain secrets held in " +
            "memory, are not exposed over HTTP. All other endpoints stay exposed. As a profile-specific value replaces " +
            "the base one, no change is made in a project where any Spring configuration file already sets " +
            "`management.endpoints.web.exposure.exclude` or includes `heapdump` explicitly. Multi-document YAML files, " +
            "environment variables and command-line arguments are not considered.";

    public static class Accumulator {
        final SpringConfigFiles springConfigFiles = new SpringConfigFiles();
        final Set<@Nullable JavaProject> projectsToSkip = new HashSet<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> springConfigFilesScanner = acc.springConfigFiles.getScanner();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                springConfigFilesScanner.visit(tree, ctx);
                if ((tree instanceof Properties.File && !FindProperties.find((Properties.File) tree, EXCLUDE_ANY_FORM, true).isEmpty()) ||
                    (tree instanceof Yaml.Documents && !FindProperty.find((Yaml.Documents) tree, EXCLUDE_ANY_FORM, true).isEmpty()) ||
                    includedEndpoints(tree).contains("heapdump")) {
                    acc.projectsToSkip.add(((SourceFile) tree).getMarkers().findFirst(JavaProject.class).orElse(null));
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return Preconditions.check(new IsPossibleSpringConfigFile(acc.springConfigFiles), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (acc.projectsToSkip.contains(((SourceFile) tree).getMarkers().findFirst(JavaProject.class).orElse(null)) ||
                    (tree instanceof Yaml.Documents && ((Yaml.Documents) tree).getDocuments().size() != 1)) {
                    return tree;
                }
                if (includedEndpoints(tree).contains("*")) {
                    // Match any path, as AddSpringProperty otherwise just edits the default application.* files
                    return new AddSpringProperty(EXCLUDE, "heapdump", null, singletonList("**"))
                            .getVisitor().visit(tree, ctx);
                }
                return tree;
            }
        });
    }

    private static List<String> includedEndpoints(Tree tree) {
        List<String> endpoints = new ArrayList<>();
        if (tree instanceof Properties.File) {
            for (Properties.Entry entry : FindProperties.find((Properties.File) tree, INCLUDE_ANY_FORM, true)) {
                addEndpoints(entry.getValue().getText(), endpoints);
            }
        } else if (tree instanceof Yaml.Documents) {
            for (Yaml.Block block : FindProperty.find((Yaml.Documents) tree, INCLUDE_ANY_FORM, true)) {
                if (block instanceof Yaml.Scalar) {
                    addEndpoints(((Yaml.Scalar) block).getValue(), endpoints);
                } else if (block instanceof Yaml.Sequence) {
                    for (Yaml.Sequence.Entry entry : ((Yaml.Sequence) block).getEntries()) {
                        if (entry.getBlock() instanceof Yaml.Scalar) {
                            addEndpoints(((Yaml.Scalar) entry.getBlock()).getValue(), endpoints);
                        }
                    }
                }
            }
        }
        return endpoints;
    }

    private static void addEndpoints(String value, List<String> endpoints) {
        for (String endpoint : value.split(",")) {
            endpoints.add(endpoint.trim());
        }
    }
}

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
package org.openrewrite.java.spring.boot2;

import lombok.Data;
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.spring.ExpandProperties;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.yaml.CoalescePropertiesVisitor;
import org.openrewrite.yaml.MergeYamlVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MergeBootstrapYamlWithApplicationYaml extends ScanningRecipe<MergeBootstrapYamlWithApplicationYaml.Accumulator> {

    private static final Pattern CONFIG_FILE = Pattern.compile("(bootstrap|application)(-[^./]+)?\\.ya?ml");

    @Getter
    final String displayName = "Merge Spring `bootstrap.yml` with `application.yml`";

    @Getter
    final String description = "In Spring Boot 2.4, the bootstrap context that loads `bootstrap.yml` is " +
            "[disabled by default](https://docs.spring.io/spring-cloud-config/reference/client.html). " +
            "Its properties should be merged with `application.yml` unless `spring-cloud-starter-bootstrap` is present as a dependency. " +
            "Profile-specific `bootstrap-{profile}.yml` files are also merged into their matching `application-{profile}.yml`. " +
            "A bootstrap file without a matching application file is renamed instead.";

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile source = (SourceFile) tree;
                Path sourcePath = source.getSourcePath();
                if (PathUtils.matchesGlob(sourcePath, "**/main/resources/*")) {
                    Matcher matcher = CONFIG_FILE.matcher(sourcePath.getFileName().toString());
                    if (matcher.matches()) {
                        Path applicationStem = sourcePath.resolveSibling("application" + (matcher.group(2) == null ? "" : matcher.group(2)));
                        boolean bootstrap = "bootstrap".equals(matcher.group(1));
                        boolean yaml = source instanceof Yaml.Documents;
                        Map<Path, ConfigFile> yamls = bootstrap ? acc.getBootstrapYamls() : acc.getApplicationYamls();
                        yamls.computeIfAbsent(applicationStem, stem -> new ConfigFile(sourcePath, yaml, bootstrap && yaml ?
                                source.printAll(new PrintOutputCapture<>(0, PrintOutputCapture.MarkerPrinter.SANITIZED)) : null));
                    }
                }
                if (!acc.isSpringCloudBootstrapPresent()) {
                    source.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(maven -> {
                        if (!maven.findDependencies("org.springframework.cloud", "spring-cloud-starter-bootstrap", Scope.Compile).isEmpty()) {
                            acc.setSpringCloudBootstrapPresent(true);
                        }
                    });
                    source.getMarkers().findFirst(GradleProject.class).ifPresent(gradle -> {
                        for (GradleDependencyConfiguration config : gradle.getConfigurations()) {
                            if (config.findResolvedDependency("org.springframework.cloud", "spring-cloud-starter-bootstrap") != null) {
                                acc.setSpringCloudBootstrapPresent(true);
                                break;
                            }
                        }
                    });
                }
                return source;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.isSpringCloudBootstrapPresent()) {
            return TreeVisitor.noop();
        }

        Map<Path, String> bootstrapByApplicationPath = new HashMap<>();
        Set<Path> bootstrapPathsToDelete = new HashSet<>();
        Map<Path, Path> bootstrapPathsToRename = new HashMap<>();
        for (Map.Entry<Path, ConfigFile> entry : acc.getBootstrapYamls().entrySet()) {
            Path applicationStem = entry.getKey();
            ConfigFile bootstrap = entry.getValue();
            String bootstrapYaml = bootstrap.getText();
            if (bootstrapYaml == null) {
                continue;
            }
            ConfigFile application = acc.getApplicationYamls().get(applicationStem);
            if (application == null) {
                String extension = PathUtils.matchesGlob(bootstrap.getSourcePath(), "**/*.yaml") ? ".yaml" : ".yml";
                bootstrapPathsToRename.put(bootstrap.getSourcePath(), applicationStem.resolveSibling(applicationStem.getFileName() + extension));
            } else if (application.isYaml()) {
                bootstrapByApplicationPath.put(application.getSourcePath(), bootstrapYaml);
                bootstrapPathsToDelete.add(bootstrap.getSourcePath());
            }
        }

        if (bootstrapByApplicationPath.isEmpty() && bootstrapPathsToRename.isEmpty()) {
            return TreeVisitor.noop();
        }

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile source = (SourceFile) tree;
                Path sourcePath = source.getSourcePath();
                if (bootstrapPathsToDelete.contains(sourcePath)) {
                    return null;
                }
                Path renamedPath = bootstrapPathsToRename.get(sourcePath);
                if (renamedPath != null) {
                    return source.withSourcePath(renamedPath);
                }
                String bootstrap = bootstrapByApplicationPath.get(sourcePath);
                if (bootstrap != null && source instanceof Yaml.Documents) {
                    source = mergeBootstrapInto((Yaml.Documents) source, parse(bootstrap), ctx);
                }
                return source;
            }
        };
    }

    private static SourceFile mergeBootstrapInto(Yaml.Documents application, Yaml.Documents bootstrap, ExecutionContext ctx) {
        Yaml.Documents a = (Yaml.Documents) new ExpandProperties(null).getVisitor().visit(application, ctx);
        Yaml.Documents b = (Yaml.Documents) new ExpandProperties(null).getVisitor().visit(bootstrap, ctx);
        assert a != null;
        assert b != null;

        List<Yaml.Document> bootstrapBaseDocuments = new ArrayList<>();
        List<Yaml.Document> bootstrapProfileDocuments = new ArrayList<>();
        for (Yaml.Document d : b.getDocuments()) {
            if (isProfileSpecific(d)) {
                bootstrapProfileDocuments.add(d);
            } else {
                bootstrapBaseDocuments.add(d);
            }
        }

        List<Yaml.Document> documents = new ArrayList<>(a.getDocuments().size() + bootstrapProfileDocuments.size());
        boolean merged = false;
        for (Yaml.Document doc : a.getDocuments()) {
            if (merged || isProfileSpecific(doc)) {
                documents.add(doc);
                continue;
            }
            Yaml.Document mergedDocument = doc;
            for (Yaml.Document d : bootstrapBaseDocuments) {
                Yaml.Document mergedDocumentOrNull = (Yaml.Document) new MergeYamlVisitor<Integer>(mergedDocument.getBlock(), d.getBlock(), true, null, null, null).visit(mergedDocument, 0, new Cursor(null, a));
                if (mergedDocumentOrNull != null) {
                    mergedDocument = mergedDocumentOrNull;
                }
            }
            documents.add(mergedDocument);
            documents.addAll(bootstrapProfileDocuments);
            merged = true;
        }
        if (!merged) {
            documents.addAll(0, ListUtils.concatAll(bootstrapBaseDocuments, bootstrapProfileDocuments));
        }

        for (int i = 1; i < documents.size(); i++) {
            Yaml.Document doc = documents.get(i);
            if (!documents.get(i - 1).getEnd().getPrefix().endsWith("\n") && !doc.getPrefix().startsWith("\n")) {
                doc = doc.withPrefix("\n" + doc.getPrefix());
            }
            if (!doc.isExplicit()) {
                doc = doc.withExplicit(true).withBlock(startOnNewLine(doc.getBlock()));
            }
            documents.set(i, doc);
        }

        //noinspection unchecked
        SourceFile coalesced = (SourceFile) new CoalescePropertiesVisitor<Integer>(null, null).visitNonNull(a.withDocuments(documents), 0);
        // Expanding and coalescing the properties copies an application file that gains nothing from the merge
        return coalesced.printAll().equals(application.printAll()) ? application : coalesced;
    }

    private static Yaml.Documents parse(String yaml) {
        return YamlParser.builder().build().parse(yaml)
                .findFirst()
                .filter(Yaml.Documents.class::isInstance)
                .map(Yaml.Documents.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Could not parse as YAML"));
    }

    private static Yaml.Block startOnNewLine(Yaml.Block block) {
        if (block instanceof Yaml.Mapping) {
            Yaml.Mapping mapping = (Yaml.Mapping) block;
            return mapping.withEntries(ListUtils.mapFirst(mapping.getEntries(), entry -> entry.withPrefix("\n" + entry.getPrefix())));
        }
        return block.withPrefix("\n" + block.getPrefix());
    }

    private static boolean isProfileSpecific(Yaml.Document document) {
        if (!FindProperty.find(document, "spring.config.activate.on-profile", true).isEmpty()) {
            return true;
        }
        for (Yaml.Block legacyProfiles : FindProperty.find(document, "spring.profiles", true)) {
            if (!(legacyProfiles instanceof Yaml.Mapping)) {
                return true;
            }
        }
        return false;
    }

    @Data
    static class Accumulator {
        final Map<Path, ConfigFile> bootstrapYamls = new LinkedHashMap<>();
        final Map<Path, ConfigFile> applicationYamls = new LinkedHashMap<>();
        boolean springCloudBootstrapPresent;
    }

    /**
     * The first bootstrap or application file scanned for an application file stem: its path, whether it is YAML,
     * and the printed YAML of a bootstrap file to merge.
     */
    @Value
    static class ConfigFile {
        Path sourcePath;
        boolean yaml;

        @Nullable
        String text;
    }
}

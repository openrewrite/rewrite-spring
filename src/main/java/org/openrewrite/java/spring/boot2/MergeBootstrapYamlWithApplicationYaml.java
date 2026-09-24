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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

public class MergeBootstrapYamlWithApplicationYaml extends ScanningRecipe<MergeBootstrapYamlWithApplicationYaml.Accumulator> {

    private static final Pattern BOOTSTRAP_FILE = Pattern.compile("bootstrap(?:-([^./]+))?\\.ya?ml");
    private static final Pattern APPLICATION_FILE = Pattern.compile("application(?:-([^./]+))?\\.ya?ml");
    private static final String BASE_PROFILE = "";

    @Getter
    final String displayName = "Merge Spring `bootstrap.yml` with `application.yml`";

    @Getter
    final String description = "In Spring Boot 2.4, the bootstrap context that loads `bootstrap.yml` is " +
            "[disabled by default](https://docs.spring.io/spring-cloud-config/reference/client.html). " +
            "Its properties should be merged with `application.yml` unless `spring-cloud-starter-bootstrap` is present as a dependency. " +
            "Profile-specific `bootstrap-{profile}.yml` files are also merged into their matching `application-{profile}.yml`.";

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
                    String fileName = sourcePath.getFileName().toString();
                    Matcher bootstrapMatcher = BOOTSTRAP_FILE.matcher(fileName);
                    if (bootstrapMatcher.matches()) {
                        acc.getBootstrapYamls().putIfAbsent(pairKey(sourcePath.getParent(), profileOrBase(bootstrapMatcher)), source);
                    } else {
                        Matcher applicationMatcher = APPLICATION_FILE.matcher(fileName);
                        if (applicationMatcher.matches()) {
                            acc.getApplicationYamls().putIfAbsent(pairKey(sourcePath.getParent(), profileOrBase(applicationMatcher)), source);
                        }
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
    public Collection<SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (acc.isSpringCloudBootstrapPresent()) {
            return emptyList();
        }
        List<SourceFile> generated = new ArrayList<>();
        for (Map.Entry<String, SourceFile> entry : acc.getBootstrapYamls().entrySet()) {
            String key = entry.getKey();
            SourceFile bootstrap = entry.getValue();
            if (!(bootstrap instanceof Yaml.Documents) || acc.getApplicationYamls().containsKey(key)) {
                continue;
            }
            String profile = profileFromKey(key);
            String extension = PathUtils.matchesGlob(bootstrap.getSourcePath(), "**/*.yaml") ? ".yaml" : ".yml";
            String fileName = (BASE_PROFILE.equals(profile) ? "application" : "application-" + profile) + extension;
            Optional<SourceFile> newApplicationYaml = YamlParser.builder().build()
                    .parse("")
                    .map(brandNewFile -> (SourceFile) brandNewFile
                            .withSourcePath(bootstrap.getSourcePath().resolveSibling(fileName)))
                    .findFirst();
            if (newApplicationYaml.isPresent()) {
                acc.getApplicationYamls().put(key, newApplicationYaml.get());
                generated.add(newApplicationYaml.get());
            }
        }
        return generated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.isSpringCloudBootstrapPresent()) {
            return TreeVisitor.noop();
        }

        Map<Path, Yaml.Documents> bootstrapByApplicationPath = new HashMap<>();
        Set<Path> bootstrapPathsToDelete = new HashSet<>();
        for (Map.Entry<String, SourceFile> entry : acc.getBootstrapYamls().entrySet()) {
            SourceFile application = acc.getApplicationYamls().get(entry.getKey());
            if (entry.getValue() instanceof Yaml.Documents && application instanceof Yaml.Documents) {
                bootstrapByApplicationPath.put(application.getSourcePath(), (Yaml.Documents) entry.getValue());
                bootstrapPathsToDelete.add(entry.getValue().getSourcePath());
            }
        }

        if (bootstrapByApplicationPath.isEmpty()) {
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
                Yaml.Documents bootstrap = bootstrapByApplicationPath.get(sourcePath);
                if (bootstrap != null && source instanceof Yaml.Documents) {
                    source = mergeBootstrapInto((Yaml.Documents) source, bootstrap, ctx);
                }
                return source;
            }
        };
    }

    private static SourceFile mergeBootstrapInto(Yaml.Documents application, Yaml.Documents bootstrap, ExecutionContext ctx) {
        AtomicBoolean merged = new AtomicBoolean(false);

        Yaml.Documents a = (Yaml.Documents) new ExpandProperties(null).getVisitor().visit(application, ctx);
        Yaml.Documents b = (Yaml.Documents) new ExpandProperties(null).getVisitor().visit(bootstrap, ctx);
        assert a != null;
        assert b != null;

        //noinspection unchecked
        return (SourceFile) new CoalescePropertiesVisitor<Integer>(null, null).visit(a.withDocuments(ListUtils.map(a.getDocuments(), doc -> {
            if (doc == null) {
                return null;
            }
            if (merged.compareAndSet(false, true) && FindProperty.find(doc, "spring.config.activate.on-profile", true).isEmpty()) {
                Yaml.Document mergedDocument = doc;
                Yaml.Document mergedDocumentOrNull;
                for (Yaml.Document d : b.getDocuments()) {
                    if (FindProperty.find(d, "spring.config.activate.on-profile", true).isEmpty()) {
                        mergedDocumentOrNull = (Yaml.Document) new MergeYamlVisitor<Integer>(mergedDocument.getBlock(), d.getBlock(), true, null, null, null).visit(mergedDocument, 0, new Cursor(new Cursor(null, a), mergedDocument));
                        if (mergedDocumentOrNull != null) {
                            mergedDocument = mergedDocumentOrNull;
                        }
                    }
                }
                return mergedDocument;
            }
            return doc;
        })), 0);
    }

    private static String profileOrBase(Matcher matcher) {
        String profile = matcher.group(1);
        return profile == null ? BASE_PROFILE : profile;
    }

    private static String pairKey(@Nullable Path parent, String profile) {
        return (parent == null ? "" : parent.toString()) + '\0' + profile;
    }

    private static String profileFromKey(String key) {
        return key.substring(key.lastIndexOf('\0') + 1);
    }

    @Data
    static class Accumulator {
        final Map<String, SourceFile> bootstrapYamls = new LinkedHashMap<>();
        final Map<String, SourceFile> applicationYamls = new LinkedHashMap<>();
        boolean springCloudBootstrapPresent;
    }
}

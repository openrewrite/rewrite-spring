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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.search.HasSourceSet;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.yaml.DeleteProperty;
import org.openrewrite.yaml.MergeYamlVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
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
        return Preconditions.check(new HasSourceSet("main").getVisitor(), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents yaml, ExecutionContext ctx) {
                if (PathUtils.matchesGlob(yaml.getSourcePath(), "**/application-*.{yml,yaml}")) {
                    acc.existingProfileFiles.add(yaml.getSourcePath());
                }
                if (PathUtils.matchesGlob(yaml.getSourcePath(), "**/application.yml")) {
                    List<Yaml.Documents> profiles = new ArrayList<>(yaml.getDocuments().size());
                    separateProfiles(yaml, profiles, ctx);
                    if (!profiles.isEmpty()) {
                        acc.modifiedMainProfileFiles.put(yaml.getSourcePath(), MainProfileFile.of(yaml));
                    }
                }
                return yaml;
            }
        });
    }

    @Override
    public Collection<SourceFile> generate(ApplicationProfiles acc, ExecutionContext ctx) {
        Collection<SourceFile> toGenerate = new ArrayList<>();
        for (MainProfileFile mainProfileFile : acc.modifiedMainProfileFiles.values()) {
            for (Yaml.Documents sf : mainProfileFile.profileFiles(ctx)) {
                if (acc.existingProfileFiles.contains(sf.getSourcePath())) {
                    acc.mergeDocumentsFrom.put(sf.getSourcePath(), mainProfileFile.getSourcePath());
                } else {
                    toGenerate.add(sf);
                }
            }
        }
        return toGenerate;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(ApplicationProfiles acc) {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents yaml, ExecutionContext ctx) {
                Yaml.Documents result = acc.modifiedMainProfileFiles.containsKey(yaml.getSourcePath()) ?
                        separateProfiles(yaml, new ArrayList<>(), ctx) : yaml;
                List<Yaml.Document> docsToMerge = docsToMerge(acc, yaml.getSourcePath(), ctx);
                if (docsToMerge != null && !result.getDocuments().isEmpty()) {
                    Yaml.Document mergedDoc = result.getDocuments().get(0);
                    for (Yaml.Document incoming : docsToMerge) {
                        Yaml.Document mergedOrNull = (Yaml.Document) new MergeYamlVisitor<Integer>(
                                mergedDoc.getBlock(), incoming.getBlock(), true, null, null, null
                        ).visit(mergedDoc, 0, new Cursor(null, result));
                        if (mergedOrNull != null) {
                            mergedDoc = mergedOrNull;
                        }
                    }
                    Yaml.Document finalMergedDoc = mergedDoc;
                    result = result.withDocuments(ListUtils.mapFirst(result.getDocuments(), doc -> finalMergedDoc));
                }
                return result;
            }
        };
    }

    /**
     * The documents to merge into a profile file that exists already, separated again from their application file.
     */
    private static @Nullable List<Yaml.Document> docsToMerge(ApplicationProfiles acc, Path profileFile, ExecutionContext ctx) {
        Path mainProfileFile = acc.mergeDocumentsFrom.get(profileFile);
        if (mainProfileFile == null) {
            return null;
        }
        List<Yaml.Document> docs = new ArrayList<>();
        for (Yaml.Documents profile : acc.modifiedMainProfileFiles.get(mainProfileFile).profileFiles(ctx)) {
            if (profile.getSourcePath().equals(profileFile)) {
                docs.addAll(profile.getDocuments());
            }
        }
        return docs;
    }

    /**
     * The application file without the documents of its profiles, which go to the files of the profiles.
     */
    private static Yaml.Documents separateProfiles(Yaml.Documents yaml, Collection<Yaml.Documents> profiles, ExecutionContext ctx) {
        return yaml.withDocuments(ListUtils.map(
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
                        Yaml.Document profileDoc = (Yaml.Document) new DeleteProperty("spring.config.activate.on-profile", null, true, null)
                                .getVisitor().visitNonNull(doc, ctx, new Cursor(null, yaml));
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
    }

    public static class ApplicationProfiles {
        Map<Path, MainProfileFile> modifiedMainProfileFiles = new HashMap<>();
        Set<Path> existingProfileFiles = new HashSet<>();
        // By profile files that exist already, the application files whose documents merge into them
        Map<Path, Path> mergeDocumentsFrom = new HashMap<>();
    }

    /**
     * An application file with profiles to separate, as printed, and what the files of its profiles take from it
     * besides their documents: all but the classpath of its source set.
     */
    @Value
    static class MainProfileFile {
        Path sourcePath;
        String yaml;
        List<Marker> markers;

        @Nullable
        UUID sourceSetId;

        @Nullable
        String sourceSetName;

        @Nullable
        FileAttributes fileAttributes;

        Charset charset;
        boolean charsetBomMarked;

        @Nullable
        Checksum checksum;

        static MainProfileFile of(Yaml.Documents yaml) {
            List<Marker> markers = new ArrayList<>();
            JavaSourceSet sourceSet = null;
            for (Marker marker : yaml.getMarkers().getMarkers()) {
                if (marker instanceof JavaSourceSet) {
                    sourceSet = (JavaSourceSet) marker;
                } else {
                    markers.add(marker);
                }
            }
            return new MainProfileFile(yaml.getSourcePath(),
                    yaml.printAll(new PrintOutputCapture<>(0, PrintOutputCapture.MarkerPrinter.SANITIZED)), markers,
                    sourceSet == null ? null : sourceSet.getId(), sourceSet == null ? null : sourceSet.getName(),
                    yaml.getFileAttributes(), yaml.getCharset(), yaml.isCharsetBomMarked(), yaml.getChecksum());
        }

        /**
         * The files of the profiles, separated from the application file parsed again.
         */
        List<Yaml.Documents> profileFiles(ExecutionContext ctx) {
            Markers markers = Markers.build(this.markers);
            if (sourceSetId != null && sourceSetName != null) {
                markers = markers.add(new JavaSourceSet(sourceSetId, sourceSetName, emptyList(), emptyMap()));
            }
            Yaml.Documents application = YamlParser.builder().build().parse(yaml)
                    .findFirst()
                    .filter(Yaml.Documents.class::isInstance)
                    .map(Yaml.Documents.class::cast)
                    .orElseThrow(() -> new IllegalArgumentException("Could not parse as YAML"))
                    .withSourcePath(sourcePath)
                    .withMarkers(markers)
                    .withFileAttributes(fileAttributes)
                    .withCharset(charset)
                    .withCharsetBomMarked(charsetBomMarked)
                    .withChecksum(checksum);
            List<Yaml.Documents> profiles = new ArrayList<>(application.getDocuments().size());
            separateProfiles(application, profiles, ctx);
            return profiles;
        }
    }
}

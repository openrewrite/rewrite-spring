/*
 * Copyright 2025 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.YamlParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
public class ConvertPropertiesToYaml extends ScanningRecipe<ConvertPropertiesToYaml.Accumulator> {

    private static final Pattern PROPERTIES_NAME_PATTERN = Pattern.compile("application(-.+)?\\.properties");
    private static final Pattern YAML_NAME_PATTERN = Pattern.compile("application(-.+)?\\.(yml|yaml)");
    // Matches candidate file names inside larger strings such as "classpath:application-dev.properties"
    private static final Pattern REFERENCED_FILE_NAME_PATTERN = Pattern.compile("application(-[^/\\\\:\\s]+)?\\.properties");

    @Option(displayName = "File extension",
            description = "The extension to use for the generated YAML files. Defaults to `yaml`.",
            valid = {"yaml", "yml"},
            example = "yml",
            required = false)
    @Nullable
    String fileExtension;

    @Override
    public String getDisplayName() {
        return "Convert Spring `application-*.properties` to `application-*.yaml`";
    }

    @Override
    public String getDescription() {
        return "Converts Spring Boot `application-*.properties` files to `application-*.yaml`. " +
                "The original `.properties` file is deleted and its comments are carried over. " +
                "Conversion is skipped (with a message) " +
                "when a corresponding `.yml` or `.yaml` file already exists.";
    }

    @Value
    static class PendingConversion {
        String yamlContent;
        Markers markers;
    }

    public static class Accumulator {
        final Map<Path, PendingConversion> toConvert = new LinkedHashMap<>();
        // parent directory -> file name stem (e.g. "application-dev") -> existing .yml/.yaml file
        final Map<Path, Map<String, Path>> existingYaml = new HashMap<>();
        final Set<String> fileNamesReferencedFromJava = new HashSet<>();
        // Paths whose conversion succeeded (YAML generated, or the file had no content
        // to carry over); only these may be deleted
        final Set<Path> converted = new HashSet<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    SourceFile source = (SourceFile) tree;
                    trackExistingYaml(acc, source);
                    trackCandidate(acc, source, ctx);
                    collectJavaReferences(acc, source);
                }
                return tree;
            }
        };
    }

    private static void trackExistingYaml(Accumulator acc, SourceFile source) {
        String fileName = fileName(source);
        if (YAML_NAME_PATTERN.matcher(fileName).matches()) {
            Path sourcePath = source.getSourcePath();
            String stem = fileName.replaceAll("\\.(yml|yaml)$", "");
            Path parent = sourcePath.getParent() != null ? sourcePath.getParent() : Paths.get("");
            acc.existingYaml.computeIfAbsent(parent, k -> new HashMap<>()).putIfAbsent(stem, sourcePath);
        }
    }

    private static void trackCandidate(Accumulator acc, SourceFile source, ExecutionContext ctx) {
        if (source instanceof Properties.File &&
                PROPERTIES_NAME_PATTERN.matcher(fileName(source)).matches() &&
                new IsPossibleSpringConfigFile().visit(source, ctx) != source) {
            acc.toConvert.put(source.getSourcePath(), new PendingConversion(
                    PropertiesToYamlConverter.convert((Properties.File) source),
                    source.getMarkers()));
        }
    }

    private static void collectJavaReferences(Accumulator acc, SourceFile source) {
        if (!(source instanceof JavaSourceFile)) {
            return;
        }
        new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, Set<String> referencedFileNames) {
                if (literal.getValue() instanceof String) {
                    Matcher reference = REFERENCED_FILE_NAME_PATTERN.matcher((String) literal.getValue());
                    while (reference.find()) {
                        referencedFileNames.add(reference.group());
                    }
                }
                return literal;
            }
        }.visit(source, acc.fileNamesReferencedFromJava);
    }

    private static String fileName(SourceFile source) {
        Path fileName = source.getSourcePath().getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    @Override
    public Collection<SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (acc.toConvert.isEmpty()) {
            return emptyList();
        }
        List<SourceFile> newFiles = new ArrayList<>();
        acc.toConvert.forEach((propertiesPath, pending) -> {
            if (skipReason(acc, propertiesPath) != null) {
                return;
            }
            if (pending.getYamlContent().isEmpty()) {
                acc.converted.add(propertiesPath);
                return;
            }
            YamlParser.builder().build()
                    .parse(pending.getYamlContent())
                    .findFirst()
                    .map(brandNew -> (SourceFile) brandNew
                            .withSourcePath(toYamlPath(propertiesPath))
                            .withMarkers(pending.getMarkers()))
                    .ifPresent(newFile -> {
                        newFiles.add(newFile);
                        acc.converted.add(propertiesPath);
                    });
        });
        return newFiles;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Properties.File)) {
                    return tree;
                }
                Properties.File propertiesFile = (Properties.File) tree;
                Path sourcePath = propertiesFile.getSourcePath();
                if (!acc.toConvert.containsKey(sourcePath)) {
                    return tree;
                }

                String skipReason = skipReason(acc, sourcePath);
                if (skipReason != null) {
                    // Attach a visible skip message instead of deleting
                    return SearchResult.found(propertiesFile, skipReason);
                }
                return acc.converted.contains(sourcePath) ? null : tree;
            }
        };
    }

    private @Nullable String skipReason(Accumulator acc, Path propertiesPath) {
        if (acc.fileNamesReferencedFromJava.contains(propertiesPath.getFileName().toString())) {
            return "Skipped: this file is referenced from Java sources (e.g. `@PropertySource`), " +
                    "which cannot load YAML files. Update those references before converting.";
        }
        Path conflictingYaml = findExistingYaml(acc, propertiesPath);
        if (conflictingYaml != null) {
            return "Skipped: a corresponding YAML file already exists at '" + conflictingYaml + "'. " +
                    "Merge these properties into it manually; when both files exist the " +
                    "`.properties` values take precedence, so converting automatically " +
                    "could change the effective configuration.";
        }
        return null;
    }

    private @Nullable Path findExistingYaml(Accumulator acc, Path propertiesPath) {
        Path parent = propertiesPath.getParent() != null ? propertiesPath.getParent() : Paths.get("");
        String stem = propertiesPath.getFileName().toString().replaceAll("\\.properties$", "");
        Map<String, Path> stems = acc.existingYaml.get(parent);
        return stems == null ? null : stems.get(stem);
    }

    private Path toYamlPath(Path propertiesPath) {
        String extension = fileExtension == null ? "yaml" : fileExtension;
        String newName = propertiesPath.getFileName().toString().replaceAll("\\.properties$", "." + extension);
        return propertiesPath.resolveSibling(newName);
    }
}

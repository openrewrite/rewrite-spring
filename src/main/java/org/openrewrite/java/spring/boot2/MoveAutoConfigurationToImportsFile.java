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
package org.openrewrite.java.spring.boot2;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.Marker;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.tree.TypeUtils.isOfClassType;

@Value
@EqualsAndHashCode(callSuper = false)
public class MoveAutoConfigurationToImportsFile extends ScanningRecipe<MoveAutoConfigurationToImportsFile.Accumulator> {
    private static final String AUTOCONFIGURATION_FILE = "org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String ENABLE_AUTO_CONFIG_KEY = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";

    @Option(displayName = "Preserve `spring.factories` files",
        description = "Don't delete the `spring.factories` for backward compatibility.",
        required = false)
    boolean preserveFactoriesFile;

    @Override
    public String getDisplayName() {
        return "Use `AutoConfiguration#imports`";
    }

    @Override
    public String getDescription() {
        return "Use `AutoConfiguration#imports` instead of the deprecated entry " +
                "`EnableAutoConfiguration` in `spring.factories` when defining " +
                "autoconfiguration classes.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        // First pass will look for any spring.factories source files to collect any auto-config classes in those files
        // and remove them. We build a map to the path of the target import file (computed relative to the spring.factories
        // file) to a list of autoconfiguration classes from the spring.factories and any markers that may have been
        // on the factory class. If we end up creating a new file, we will copy the markers to this file as well.

        // We also look for any existing import files (because we may need to merge entries from the spring.factories into
        // an existing file).

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof PlainText) {
                    PlainText source = ((PlainText) tree);
                    Path sourcePath = source.getSourcePath();
                    if (sourcePath.endsWith("spring.factories")) {
                        Set<String> configs = new HashSet<>();
                        extractAutoConfigsFromSpringFactory(source, configs);
                        if (!configs.isEmpty()) {
                            acc.getExistingSpringFactories().add(sourcePath);
                            acc.getTargetImports().put(sourcePath.getParent().resolve("spring/" + AUTOCONFIGURATION_FILE),
                                    new TargetImports(configs, source.getMarkers().getMarkers()));
                            acc.getAllFoundConfigs().addAll(configs);
                        }
                    } else if (sourcePath.endsWith(AUTOCONFIGURATION_FILE)) {
                        acc.getExistingImportFiles().add(sourcePath);
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        List<SourceFile> newImportFiles = new ArrayList<>();
        for (Map.Entry<Path, TargetImports> entry : acc.getTargetImports().entrySet()) {
            if (entry.getValue().getAutoConfigurations().isEmpty() || acc.getExistingImportFiles().contains(entry.getKey())) {
                continue;
            }

            List<String> finalList = new ArrayList<>(entry.getValue().getAutoConfigurations());
            Collections.sort(finalList);

            PlainTextParser parser = new PlainTextParser();
            PlainText brandNewFile = parser.parse(String.join("\n", finalList))
                .map(PlainText.class::cast)
                .findFirst()
                .get();
            newImportFiles.add(brandNewFile
                    .withSourcePath(entry.getKey())
                    .withMarkers(brandNewFile.getMarkers().withMarkers(entry.getValue().getMarkers()))
            );
        }

        if (!newImportFiles.isEmpty()) {
            return newImportFiles;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        Set<Path> mergeTargets = acc.getExistingImportFiles().stream().filter(acc.getTargetImports()::containsKey).collect(Collectors.toSet());
        if (mergeTargets.isEmpty() && acc.getAllFoundConfigs().isEmpty() && acc.getExistingSpringFactories().isEmpty()) {
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
                if (tree instanceof PlainText) {
                    if (mergeTargets.contains(sourcePath)) {
                        //If there is both a spring.factories and an existing imports file, merge the contents of both into the import
                        tree = mergeEntries(source, acc.getTargetImports().get(sourcePath).getAutoConfigurations());
                    } else if (acc.getExistingSpringFactories().contains(sourcePath)) {
                        tree = extractAutoConfigsFromSpringFactory((PlainText) source, new HashSet<>());
                    }
                } else if (tree instanceof J.CompilationUnit) {
                    tree = new AddAutoConfigurationAnnotation(acc.getAllFoundConfigs()).visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    @Nullable
    private PlainText extractAutoConfigsFromSpringFactory(PlainText springFactory, Set<String> configs) {
        String contents = springFactory.getText();
        int state = 0;
        int index = 0;

        StringBuilder currentKey = new StringBuilder();
        StringBuilder currentValue = new StringBuilder();
        int keyIndexStart = 0;
        int valueIndexEnd = 0;

        while (index < contents.length()) {
            if (contents.charAt(index) == '\\' && isLineBreakOrEof(contents, index + 1)) {
                //If this is a line continuous, advance to the next line and then chew up any white space.
                index = advanceToNextLine(contents, index);
                index = advancePastWhiteSpace(contents, index);
            }
            if (state == 0) {
                // Find New Key
                index = advancePastWhiteSpace(contents, index);
                if (index >= contents.length()) {
                    break;
                }
                if (contents.charAt(index) == '#') {
                    //Comment
                    index = advanceToNextLine(contents, index);
                } else {
                    state = 1;
                }
                continue;
            } else if (state == 1) {
                if (isLineBreakOrEof(contents, index)) {
                    //Building a key and encountered a line ending, if there is a key, the value is null, reset
                    //and continue;
                    currentKey.setLength(0);
                    currentValue.setLength(0);
                    state = 0;
                } else if (contents.charAt(index) == '=' || contents.charAt(index) == ':' || Character.isWhitespace(contents.charAt(index))) {
                    state = 2;
                } else {
                    if (currentKey.length() == 0) {
                        keyIndexStart = index;
                    }
                    currentKey.append(contents.charAt(index));
                }
            } else {
                //State == 2
                //Building Value
                if (isLineBreakOrEof(contents, index)) {
                    //End of value!
                    if (ENABLE_AUTO_CONFIG_KEY.contentEquals(currentKey)) {
                        //Found the key, lets break now.
                        index = advanceToNextLine(contents, index);
                        valueIndexEnd = Math.min(index, contents.length());
                        break;
                    } else {
                        currentKey.setLength(0);
                        currentValue.setLength(0);
                        state = 0;
                    }
                } else {
                    currentValue.append(contents.charAt(index));
                }
            }
            index++;
        }

        if (ENABLE_AUTO_CONFIG_KEY.contentEquals(currentKey)) {
            Stream.of(currentValue.toString().split(",")).map(String::trim).forEach(configs::add);
            if (preserveFactoriesFile) {
                return springFactory;
            } else {
                String newContent = contents.substring(0, keyIndexStart) + contents.substring(valueIndexEnd == 0 ? contents.length() : valueIndexEnd);
                return newContent.isEmpty() ? null : springFactory.withText(newContent);
            }
        } else {
            return springFactory;
        }
    }

    private static int advancePastWhiteSpace(String contents, int index) {
        while (index < contents.length() && contents.charAt(index) != '\r' && contents.charAt(index) != '\n' && Character.isWhitespace(contents.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int advanceToNextLine(String contents, int index) {
        while (index < contents.length() && !isLineBreakOrEof(contents, index)) {
            index++;
        }
        if (index + 1 < contents.length() && contents.charAt(index) == '\r' && contents.charAt(index + 1) == '\n') {
            index = index + 2;
        } else {
            index++;
        }
        return index;
    }

    private static boolean isLineBreakOrEof(String contents, int index) {
        if (index == contents.length()) {
            return true;
        }
        char first = contents.charAt(index);
        Character second = index + 1 < contents.length() ? contents.charAt(index + 1) : null;
        return (second != null && first == '\r' && second == '\n') || first == '\r' || first == '\n';
    }

    private static SourceFile mergeEntries(SourceFile before, Set<String> configClasses) {
        PlainText plainText = (PlainText) before;
        Set<String> original = new HashSet<>(Arrays.asList(plainText.getText().split("\n")));
        Set<String> merged = new TreeSet<>(configClasses);
        merged.addAll(original);

        if (merged.size() != original.size()) {
            return plainText.withText(String.join("\n", merged));
        } else {
            return before;
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class AddAutoConfigurationAnnotation extends JavaIsoVisitor<ExecutionContext> {
        private static final String CONFIGURATION_FQN = "org.springframework.context.annotation.Configuration";
        private static final String AUTO_CONFIGURATION_FQN = "org.springframework.boot.autoconfigure.AutoConfiguration";

        Set<String> fullyQualifiedConfigClasses;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

            if (c.getType() != null && fullyQualifiedConfigClasses.contains(c.getType().getFullyQualifiedName())) {
                c = maybeAddAutoconfiguration(c, ctx);
                c = maybeRemoveConfiguration(c);
            }
            return c;
        }

        private J.ClassDeclaration maybeRemoveConfiguration(J.ClassDeclaration c) {
            if (isAnnotatedWith(c, CONFIGURATION_FQN)) {
                List<J.Annotation> withoutConfiguration = ListUtils.map(c.getLeadingAnnotations(), annotation -> {
                    if (isOfClassType(annotation.getAnnotationType().getType(), CONFIGURATION_FQN)) {
                        return null;
                    }
                    return annotation;
                });

                c = c.withLeadingAnnotations(withoutConfiguration);
                maybeRemoveImport(CONFIGURATION_FQN);
            }
            return c;
        }

        private J.ClassDeclaration maybeAddAutoconfiguration(J.ClassDeclaration c, ExecutionContext ctx) {
            if (!isAnnotatedWith(c, AUTO_CONFIGURATION_FQN)) {
                JavaTemplate addAnnotationTemplate = JavaTemplate.builder("@AutoConfiguration")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-boot-autoconfigure-2.7.*"))
                        .imports(AUTO_CONFIGURATION_FQN)
                        .build();

                c = addAnnotationTemplate.apply(getCursor(), c.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                maybeAddImport(AUTO_CONFIGURATION_FQN);
            }
            return c;
        }

        private boolean isAnnotatedWith(J.ClassDeclaration c, String annotationFqn) {
            return c.getLeadingAnnotations().stream()
                .anyMatch(annotation -> isOfClassType(annotation.getAnnotationType().getType(), annotationFqn));
        }
    }

    @Value
    static class Accumulator {
        Set<Path> existingSpringFactories = new HashSet<>();
        Set<Path> existingImportFiles = new HashSet<>();
        Set<String> allFoundConfigs = new HashSet<>();
        Map<Path, TargetImports> targetImports = new HashMap<>();
    }

    /**
     * Used to track the auto configurations defined in `spring.factories` (along with any markers on that file)
     */
    @Value
    static class TargetImports {
        Set<String> autoConfigurations;
        List<Marker> markers;
    }
}

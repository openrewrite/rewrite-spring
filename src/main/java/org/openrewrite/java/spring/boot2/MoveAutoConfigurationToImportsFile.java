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

import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.Marker;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MoveAutoConfigurationToImportsFile extends Recipe {

    private static final String AUTOCONFIGURATION_FILE = "org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final String ENABLE_AUTO_CONFIG_KEY = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";
    @Override
    public String getDisplayName() {
        return "Use `org.springframework.boot.autoconfigure.AutoConfiguration.imports`";
    }

    @Override
    public String getDescription() {
        return "Use `org.springframework.boot.autoconfigure.AutoConfiguration.imports` instead of the deprecated entry " +
               "`org.springframework.boot.autoconfigure.EnableAutoConfiguration` in `spring.factories` when defining " +
               "auto-configuration classes.";
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {

        // First pass will look for any spring.factories source files to collect any auto-config classes in those files
        // and remove them. We build a map to the path of the target import file (computed relative to the spring.factories
        // file) to a list of auto-configuration classes from the spring.factories and any markers that may have been
        // on the factory class. If we end up creating a new file, we will copy the markers to this file as well.
        Map<Path, TargetImports> targetImportFileMap = new HashMap<>();

        // We also look for any existing import files (because we may need to merge entries from the spring.factories into
        // an existing file.
        Set<Path> existingImportFiles = new HashSet<>();

        Set<String> allFoundConfigs = new HashSet<>();

        List<SourceFile> after = ListUtils.map(before, s -> {
            if (s instanceof PlainText) {
                if (s.getSourcePath().endsWith("spring.factories")) {
                    Set<String> configs = new HashSet<>();
                    PlainText plainText = extractAutoConfigsFromSpringFactory(((PlainText) s), configs);
                    if (!configs.isEmpty()) {
                        targetImportFileMap.put(s.getSourcePath().getParent().resolve("spring/" + AUTOCONFIGURATION_FILE),
                                new TargetImports(configs, s.getMarkers().getMarkers()));
                        allFoundConfigs.addAll(configs);
                    }
                    return plainText;
                } else if (s.getSourcePath().endsWith(AUTOCONFIGURATION_FILE)) {
                    existingImportFiles.add(s.getSourcePath());
                }
            }
            return s;
        });

        Set<Path> mergeTargets = existingImportFiles.stream().filter(targetImportFileMap::containsKey).collect(Collectors.toSet());
        after = ListUtils.map(after, s -> {
            if (s instanceof PlainText) {
                if (mergeTargets.contains(s.getSourcePath())) {
                    //If there is both a spring.factories and an existing imports file, merge the contents of both into the
                    //import.
                    return mergeEntries(s, targetImportFileMap.get(s.getSourcePath()).getAutoConfigurations());
                }
            } else if (s instanceof J.CompilationUnit) {
                s = (J.CompilationUnit) new AddAutoConfigurationAnnotation(allFoundConfigs).visit(s, ctx);
            }
            return s;
        });

        // Remove any files that have been merged so they do not get added below.
        for (Path existing : mergeTargets) {
            targetImportFileMap.remove(existing);
        }

        //And add any new files!
        return ListUtils.concatAll(after, createNewImportFiles(targetImportFileMap));
    }

    @Nullable
    private static PlainText extractAutoConfigsFromSpringFactory(PlainText springFactory, Set<String> configs) {

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
                    if (ENABLE_AUTO_CONFIG_KEY.equals(currentKey.toString())) {
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
        if (ENABLE_AUTO_CONFIG_KEY.equals(currentKey.toString())) {
            configs.addAll(Arrays.stream(currentValue.toString().split(",")).map(String::trim).collect(Collectors.toSet()));
            String newContent = contents.substring(0, keyIndexStart) + contents.substring(valueIndexEnd == 0 ? contents.length() : valueIndexEnd);
            return newContent.isEmpty() ? null : springFactory.withText(newContent);
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
        if (index + 1 < contents.length() && contents.charAt(index) == '\r' && contents.charAt(index +1) == '\n') {
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
        Set<String> original = Arrays.stream(plainText.getText().split("\n")).collect(Collectors.toSet());
        Set<String> merged = new HashSet<>(configClasses);
        merged.addAll(original);

        if (merged.size() != original.size()) {
            List<String> finalList = new ArrayList<>(merged);
            Collections.sort(finalList);
            return plainText.withText(String.join("\n", finalList));
        } else {
            return before;
        }
    }

    @Nullable
    private List<SourceFile> createNewImportFiles(Map<Path, TargetImports> destinationToConfigurationClasses) {
        List<SourceFile> newImportFiles = new ArrayList<>();
        for (Map.Entry<Path, TargetImports> entry : destinationToConfigurationClasses.entrySet()) {
            if (entry.getValue().getAutoConfigurations().isEmpty()) {
                continue;
            }

            List<String> finalList = new ArrayList<>(entry.getValue().getAutoConfigurations());
            Collections.sort(finalList);

            PlainTextParser parser = new PlainTextParser();
            PlainText brandNewFile = parser.parse(String.join("\n", finalList)).get(0);
            newImportFiles.add(
                    brandNewFile
                            .withSourcePath(entry.getKey())
                            .withMarkers(brandNewFile.getMarkers().withMarkers(entry.getValue().getMarkers()))
            );
        }

        if (!newImportFiles.isEmpty()) {
            return newImportFiles;
        } else {
            return null;
        }
    }

    private static class AddAutoConfigurationAnnotation extends JavaIsoVisitor<ExecutionContext> {

        @Language("java")
        private static final String autoConfigStub =
                "package org.springframework.boot.autoconfigure;\n" +
                "\n" +
                "import java.lang.annotation.Documented;\n" +
                "import java.lang.annotation.ElementType;\n" +
                "import java.lang.annotation.Retention;\n" +
                "import java.lang.annotation.RetentionPolicy;\n" +
                "import java.lang.annotation.Target;\n" +
                "@Target(ElementType.TYPE)\n" +
                "@Retention(RetentionPolicy.RUNTIME)\n" +
                "@Documented\n" +
                "public @interface AutoConfiguration {\n" +
                "   String value() default \"\";\n" +
                "   Class<?>[] before() default {};\n" +
                "   String[] beforeName() default {};\n" +
                "   Class<?>[] after() default {};\n" +
                "   String[] afterName() default {};\n" +
                "}";

        private final Set<String> fullyQualifiedConfigClasses;

        private final JavaTemplate addAnnotationTemplate = JavaTemplate.builder(this::getCursor, "@AutoConfiguration")
                .javaParser(() -> JavaParser.fromJavaVersion().dependsOn(autoConfigStub).build())
                .imports("org.springframework.boot.autoconfigure.AutoConfiguration")
                .build();

        private AddAutoConfigurationAnnotation(Set<String> fullyQualifiedConfigClasses) {
            this.fullyQualifiedConfigClasses= fullyQualifiedConfigClasses;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, executionContext);

            if (c.getType() != null && fullyQualifiedConfigClasses.contains(c.getType().getFullyQualifiedName())) {
                doAfterVisit(new RemoveAnnotation("@org.springframework.context.annotation.Configuration"));
                c = c.withTemplate(addAnnotationTemplate, c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                maybeAddImport("org.springframework.boot.autoconfigure.AutoConfiguration");
            }
            return c;
        }
    }

    /**
     * Used to track the auto configurations defined in spring.factories (along with any markers on that file)
     */
    @Value
    private static class TargetImports {
        Set<String> autoConfigurations;
        List<Marker> markers;
    }

}

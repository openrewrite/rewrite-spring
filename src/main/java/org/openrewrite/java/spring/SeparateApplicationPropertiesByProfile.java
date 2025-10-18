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
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.marker.Markers;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.properties.tree.Properties.Comment.Delimiter.EXCLAMATION_MARK;
import static org.openrewrite.properties.tree.Properties.Comment.Delimiter.HASH_TAG;

@Value
@EqualsAndHashCode(callSuper = false)
public class SeparateApplicationPropertiesByProfile extends ScanningRecipe<SeparateApplicationPropertiesByProfile.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Separate `application.properties` by profile";
    }

    @Override
    public String getDescription() {
        return "Separating `application.properties` into separate files based on profiles.";
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
                if (!(tree instanceof Properties.File)) {
                    return tree;
                }

                Properties.File propertyFile = (Properties.File) tree;
                Optional<JavaProject> javaProject = propertyFile.getMarkers().findFirst(JavaProject.class);
                if (!javaProject.isPresent()) {
                    return tree;
                }

                // Get or create the module info using the JavaProject marker as the key
                ModulePropertyInfo moduleInfo = acc.moduleProperties.computeIfAbsent(javaProject.get(), k -> new ModulePropertyInfo());
                if (propertyFile.getSourcePath().endsWith("application.properties")) {
                    moduleInfo.applicationProperties = propertyFile.getSourcePath();
                    moduleInfo.extractedProfileProperties = getNewApplicationPropertyFileInfo(propertyFile);

                } else if (propertyFile.getSourcePath().getFileName().toString().matches("application-[^/]+\\.properties")) {
                    moduleInfo.existingProfileProperties.add(propertyFile.getSourcePath());
                }
                return tree;
            }
        };
    }


    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        Set<SourceFile> newSourceFiles = new HashSet<>();
        PropertiesParser propertiesParser = PropertiesParser.builder().build();
        for (Map.Entry<JavaProject, ModulePropertyInfo> entry : acc.moduleProperties.entrySet()) {
            JavaProject javaProject = entry.getKey();
            ModulePropertyInfo moduleInfo = entry.getValue();
            for (Path fileToCreate : moduleInfo.extractedProfileProperties.keySet()) {
                if (!moduleInfo.existingProfileProperties.contains(fileToCreate)) {
                    newSourceFiles.addAll(propertiesParser.parse("")
                            .map(brandNewFile -> (SourceFile) brandNewFile.withSourcePath(fileToCreate)
                                    .withMarkers(Markers.build(singletonList(javaProject))))
                            .collect(toList()));
                }
            }
        }
        return newSourceFiles;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new PropertiesVisitor<ExecutionContext>() {
            @Override
            public Properties visitFile(Properties.File file, ExecutionContext ctx) {
                Optional<JavaProject> javaProject = file.getMarkers().findFirst(JavaProject.class);
                if (!javaProject.isPresent()) {
                    return file;
                }

                ModulePropertyInfo moduleInfo = acc.moduleProperties.get(javaProject.get());
                if (moduleInfo == null) {
                    return file;
                }

                return file.getSourcePath().endsWith("application.properties") ?
                        deleteFromApplicationProperties(file) :
                        appendToExistingPropertiesFile(file, moduleInfo.extractedProfileProperties.get(file.getSourcePath()));
            }
        };
    }

    private Properties appendToExistingPropertiesFile(Properties.File file, @Nullable List<Properties.Content> contentToAppend) {
        if (contentToAppend == null || contentToAppend.isEmpty()) {
            return file;
        }
        return file.withContent(Stream.concat(file.getContent().stream(), contentToAppend.stream()).collect(toList()));
    }

    private Properties deleteFromApplicationProperties(Properties.File applicationProperties) {
        List<Properties.Content> newContent = new ArrayList<>();
        for (Properties.Content c : applicationProperties.getContent()) {
            if (isSeparator(c)) {
                break;
            }
            newContent.add(c);
        }
        return applicationProperties.getContent().equals(newContent) ? applicationProperties :
                applicationProperties.withContent(newContent);
    }

    private Map<Path, List<Properties.Content>> getNewApplicationPropertyFileInfo(Properties.File propertyFile) {
        Path applicationProperties = propertyFile.getSourcePath();
        List<Properties.Content> contentList = propertyFile.getContent();

        Map<Path, List<Properties.Content>> map = new HashMap<>();
        int index = 0;
        while (index < contentList.size()) {
            if (isSeparator(contentList.get(index))) {
                List<Properties.Content> newContent = getContentForNewFile(contentList, ++index);
                if (!newContent.isEmpty() && newContent.get(0) instanceof Properties.Entry) {
                    String profileName = ((Properties.Entry) newContent.get(0)).getValue().getText();
                    map.put(applicationProperties.resolveSibling(String.format("application-%s.properties", profileName)),
                            newContent.subList(1, newContent.size()));
                }
            }
            index++;
        }
        return map;
    }

    private List<Properties.Content> getContentForNewFile(List<Properties.Content> contentList, int index) {
        List<Properties.Content> list = new ArrayList<>();
        while (index < contentList.size() && !isSeparator(contentList.get(index))) {
            if (contentList.get(index) instanceof Properties.Entry &&
                    "spring.config.activate.on-profile".equals
                            (((Properties.Entry) contentList.get(index)).getKey())) {
                list.add(0, contentList.get(index));
            } else {
                list.add(contentList.get(index));
            }
            index++;
        }
        return list;
    }

    private boolean isSeparator(Properties.Content c) {
        return c instanceof Properties.Comment &&
                "---".equals(((Properties.Comment) c).getMessage()) &&
                ((((Properties.Comment) c).getDelimiter() == HASH_TAG) ||
                        ((Properties.Comment) c).getDelimiter() == EXCLAMATION_MARK);
    }

    public static class Accumulator {
        // Map from a module's JavaProject marker to its property file info
        Map<JavaProject, ModulePropertyInfo> moduleProperties = new HashMap<>();
    }


    public static class ModulePropertyInfo {
        @Nullable Path applicationProperties;
        Set<Path> existingProfileProperties = new HashSet<>();
        Map<Path, List<Properties.Content>> extractedProfileProperties = new HashMap<>();
    }
}

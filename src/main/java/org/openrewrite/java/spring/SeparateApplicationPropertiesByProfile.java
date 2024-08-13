package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.properties.CreatePropertiesFile;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Value
@EqualsAndHashCode(callSuper = true)
public class SeparateApplicationPropertiesByProfile extends ScanningRecipe<SeparateApplicationPropertiesByProfile.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Separate application.properties by profile";
    }

    @Override
    public String getDescription() {
        return "Separating application.properties into separate files based on profiles while appending to any existing application-profile.properties.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                if (!(tree instanceof Properties.File))
                    return tree;

                Properties.File propertyFile = (Properties.File) tree;
                String sourcePath = PathUtils.separatorsToUnix(propertyFile.getSourcePath().toString());

                if (sourcePath.matches("application.properties"))
                    acc.propertyFileContent = getNewApplicationPropertyFileInfo(propertyFile.getContent());

                if (sourcePath.matches("application-.+\\.properties"))
                    acc.existingPropertiesFiles.put(sourcePath, propertyFile);

                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (acc.propertyFileContent.isEmpty())
            return Collections.emptyList();

        Set<SourceFile> newApplicationPropertiesFiles = new HashSet<>();

        for (Map.Entry<String, List<Properties.Content>> entry : acc.propertyFileContent.entrySet()) {
            String fileName = entry.getKey();
            @Language("properties") String fileContent = getNewFileContentString(entry.getValue());

            // generate new application-profile.properties file if it does NOT already exist
            if (!acc.existingPropertiesFiles.containsKey(fileName))
                newApplicationPropertiesFiles.
                        add(new CreatePropertiesFile(fileName, fileContent, null).
                                generate(new AtomicBoolean(true), ctx).
                                iterator().
                                next());
        }

        return newApplicationPropertiesFiles;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new PropertiesVisitor<ExecutionContext>() {
            @Override
            public Properties visitFile(Properties.File file, ExecutionContext ctx) {
                String fileName = file.getSourcePath().toString();

                if (fileName.equals("application.properties"))
                    return deleteFromApplicationProperties(file);

                // append new content if the application-profile.properties file exists AND
                // it has profile configurations set in application.properties
                if (acc.existingPropertiesFiles.containsKey(fileName) && acc.propertyFileContent.containsKey(fileName))
                    return appendToExistingPropertiesFile(file, acc.propertyFileContent.get(fileName));

                return file;
            }
        };
    }

    private Properties appendToExistingPropertiesFile(Properties.File file, List<Properties.Content> contentToAppend) {
        return file.withContent(
                Stream.concat(file.getContent().stream(), contentToAppend.stream()).
                        collect(Collectors.toList()));
    }

    private String getNewFileContentString(List<Properties.Content> content) {
        StringBuilder fileContent = new StringBuilder();

        for (Properties.Content c : content) {
            if (c instanceof Properties.Entry)
                fileContent.
                        append(((Properties.Entry) c).getKey()).
                        append("=").
                        append(((Properties.Entry) c).getValue().getPrefix()).
                        append(((Properties.Entry) c).getValue().getText()).
                        append("\n");

            else if (c instanceof Properties.Comment)
                fileContent.
                        append(((Properties.Comment) c).getMessage()).
                        append("\n");
        }

        return fileContent.toString();
    }

    private Properties deleteFromApplicationProperties(Properties.File applicationProperties) {
        List<Properties.Content> newContent = new ArrayList<>();

        for (Properties.Content c : applicationProperties.getContent()) {
            if (isSeparator(c))
                break;

            newContent.add(c);
        }

        if (applicationProperties.getContent().equals(newContent))
            return applicationProperties;

        return applicationProperties.withContent(newContent);
    }

    private Map<String, List<Properties.Content>> getNewApplicationPropertyFileInfo(List<Properties.Content> contentList) {
        Map<String, List<Properties.Content>> map = new HashMap<>();
        int index = 0;

        while (index < contentList.size()) {

            if (isSeparator(contentList.get(index))) {
                List<Properties.Content> newContent = getContentForNewFile(contentList, ++index);

                map.put("application-" + ((Properties.Entry) newContent.get(0)).getValue().getText() + ".properties",
                        newContent.subList(1, newContent.size()));
            }
            index++;
        }

        return map;
    }

    private List<Properties.Content> getContentForNewFile(List<Properties.Content> contentList, int index) {
        List<Properties.Content> list = new ArrayList<>();

        while (index < contentList.size() && !isSeparator(contentList.get(index))) {

            if (contentList.get(index) instanceof Properties.Entry &&
                    ((Properties.Entry) contentList.get(index)).getKey().equals
                            ("spring.config.activate.on-profile"))
                list.add(0, contentList.get(index));

            else
                list.add(contentList.get(index));

            index++;
        }

        return list;
    }

    private boolean isSeparator(Properties.Content c) {
        return c instanceof Properties.Comment &&
                ((Properties.Comment) c).getMessage().equals("---") &&
                ((((Properties.Comment) c).getDelimiter().equals(Properties.Comment.Delimiter.valueOf("HASH_TAG"))) ||
                        ((Properties.Comment) c).getDelimiter().equals(Properties.Comment.Delimiter.valueOf("EXCLAMATION_MARK")));
    }

    public static class Accumulator {
        Map<String, Properties.File> existingPropertiesFiles = new HashMap<>();
        Map<String, List<Properties.Content>> propertyFileContent = new HashMap<>();
    }
}



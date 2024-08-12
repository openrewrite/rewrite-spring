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


@Value
@EqualsAndHashCode(callSuper = true)
public class SeparateApplicationPropertiesByProfile extends ScanningRecipe<SeparateApplicationPropertiesByProfile.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Separate application.properties by profile";
    }

    @Override
    public String getDescription() {
        return "Separating application.properties into separate files based on profiles.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new PropertiesVisitor<ExecutionContext>() {
            @Override
            public Properties visitFile(Properties.File file, ExecutionContext ctx) {
                if (!file.getSourcePath().toString().equals("application.properties")) return file;

                return deleteFromApplicationProperties(file);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                if (!(tree instanceof Properties.File)) return tree;

                Properties.File propertyFile = (Properties.File) tree;
                String sourcePath = PathUtils.separatorsToUnix(propertyFile.getSourcePath().toString());

                if (sourcePath.matches("application.properties"))
                    acc.existingApplicationProperties = propertyFile;

                if (sourcePath.matches("application-.+\\.properties")) {
                    String s = getNewFileContentString(propertyFile.getContent());
                    acc.existingApplicationEnvProperties.put(sourcePath, s);
                }

                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (acc.existingApplicationProperties == null) return Collections.emptyList();

        for (Map.Entry<String, List<Properties.Content>> entry : this.getNewApplicationPropertyFileInfo(acc).entrySet()) {
            String fileName = entry.getKey();
            @Language("properties") String fileContent = getNewFileContentString(entry.getValue());

            if (acc.existingApplicationEnvProperties.containsKey(fileName))
                fileContent += acc.existingApplicationEnvProperties.get(fileName) + "\n";

            acc.newApplicationPropertyFiles.
                    add(new CreatePropertiesFile(fileName, fileContent, true).
                            generate(new AtomicBoolean(true), ctx).
                            iterator().
                            next()
                    );

        }

        return acc.newApplicationPropertyFiles;
    }

    private String getNewFileContentString(List<Properties.Content> content) {
        String fileContent = "";
        for (Properties.Content c : content) {
            if (c instanceof Properties.Entry)
                fileContent += ((Properties.Entry) c).getKey() + "=" + ((Properties.Entry) c).getValue().getText() + "\n";

            else if (c instanceof Properties.Comment)
                fileContent += ((Properties.Comment) c).getMessage() + "\n";

        }
        return fileContent;
    }

    private Properties deleteFromApplicationProperties(Properties.File applicationProperties) {
        List<Properties.Content> newContent = new ArrayList<>();

        for (Properties.Content c : applicationProperties.getContent()) {
            if (isSeparator(c))
                break;

            newContent.add(c);
        }

        if (applicationProperties.getContent().equals(newContent)) return applicationProperties;

        return applicationProperties.withContent(newContent);
    }

    private Map<String, List<Properties.Content>> getNewApplicationPropertyFileInfo(Accumulator acc) {
        if (acc.existingApplicationProperties == null) return new HashMap<>();

        Map<String, List<Properties.Content>> map = new HashMap<>();
        List<Properties.Content> contentList = acc.existingApplicationProperties.getContent();
        int index = 0;

        while (index < contentList.size()) {
            if (isSeparator(contentList.get(index))) {
                // index++;
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
                    ((Properties.Entry) contentList.get(index)).getKey().equals("spring.config.activate.on-profile"))
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
        @Nullable
        Properties.File existingApplicationProperties;

        Set<SourceFile> newApplicationPropertyFiles = new HashSet<>();
        Map<String, String> existingApplicationEnvProperties = new HashMap<>();
    }
}



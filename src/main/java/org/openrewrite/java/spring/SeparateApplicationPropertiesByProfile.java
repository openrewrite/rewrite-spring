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
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return Preconditions.check(
                new FindSourceFiles("**/application.properties"),
                new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                        if (!(tree instanceof SourceFile)) return tree;

                        SourceFile sourceFile = (SourceFile) tree;
                        String sourcePath = PathUtils.separatorsToUnix(sourceFile.getSourcePath().toString());

                        if (sourcePath.endsWith("application.properties"))
                            acc.existingApplicationProperties = sourceFile;

                        return tree;
                    }
                });
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (acc.existingApplicationProperties == null) return Collections.emptyList();

        for (Map.Entry<String, List<String>> entry : this.getNewApplicationPropertyFileInfo(acc).entrySet()) {
            String fileName = entry.getKey();
            @Language("properties") String fileContent = String.join("\n", entry.getValue());

            acc.newApplicationPropertyFiles.
                    add(new CreatePropertiesFile(fileName, fileContent, null).
                            generate(new AtomicBoolean(true), ctx).
                            iterator().
                            next()
                    );
        }

        return acc.newApplicationPropertyFiles;
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

    private Properties deleteFromApplicationProperties(Properties.File applicationProperties) {
        List<Properties.Content> newContent = new ArrayList<>();

        for (Properties.Content c : applicationProperties.getContent()) {

            if (c instanceof Properties.Comment &&
                    ((Properties.Comment) c).getMessage().equals("---") &&
                    ((((Properties.Comment) c).getDelimiter().equals(Properties.Comment.Delimiter.valueOf("HASH_TAG"))) ||
                            ((Properties.Comment) c).getDelimiter().equals(Properties.Comment.Delimiter.valueOf("EXCLAMATION_MARK")))) break;

            newContent.add(c);
        }

        if (applicationProperties.getContent().equals(newContent)) return applicationProperties;

        return applicationProperties.withContent(newContent);
    }

    private Map<String, List<String>> getNewApplicationPropertyFileInfo(Accumulator acc) {
        if (acc.existingApplicationProperties == null) return new HashMap<>();

        Map<String, List<String>> map = new HashMap<>();
        String[] arr = acc.existingApplicationProperties.printAll().split("\n");
        int index = 0;

        while (index < arr.length) {
            if (arr[index].equals("#---") || arr[index].equals("!---")) {
                index++;
                List<String> list = this.getLinesForNewFile(arr, index);
                map.put(list.get(0), list.subList(1, list.size()));
            }
            index++;
        }

        return map;
    }

    private List<String> getLinesForNewFile(String[] arr, int index) {
        List<String> list = new ArrayList<>();

        while (index < arr.length && !arr[index].equals("#---") && !arr[index].equals("!---")) {
            if (arr[index].startsWith("spring.config.activate.on-profile=")) list.add(
                    0,
                    "application-" +
                            arr[index].split("=")[1] +
                            ".properties");

            else if (!arr[index].isEmpty()) list.add(arr[index]);

            index++;
        }

        return list;
    }

    public static class Accumulator {
        @Nullable SourceFile existingApplicationProperties;
        Set<SourceFile> newApplicationPropertyFiles = new HashSet<>();
    }
}


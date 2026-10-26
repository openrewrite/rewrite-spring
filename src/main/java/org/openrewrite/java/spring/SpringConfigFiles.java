/*
 * Copyright 2026 the original author or authors.
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

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SourceSet;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Pom;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.text.PlainText;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * What the scan of a {@link ScanningRecipe} learns about which YAML and properties files are Spring configuration.
 * Besides the files in a source set, these are the files the repository points Spring at with
 * {@code spring.config.import}, {@code spring.config.location} or {@code spring.config.additional-location} (as
 * properties, environment variables or arguments) or with {@code @PropertySource} and {@code @TestPropertySource}, and
 * the {@code application*} and {@code bootstrap*} files Spring Boot loads from the directory of a module whose build
 * uses Spring Boot.
 */
public class SpringConfigFiles {

    private static final String SPRING_BOOT = "org.springframework.boot";
    private static final Path ROOT = Paths.get("");
    private static final Pattern CONFIG_NAME = Pattern.compile("(application|bootstrap)(-.*)?\\.(properties|ya?ml)");
    private static final Pattern DOCKERFILE = Pattern.compile(
            "^dockerfile\\b|\\.dockerfile$|^containerfile\\b|\\.containerfile$", Pattern.CASE_INSENSITIVE);
    // spring.config.import, spring.config.location and spring.config.additional-location, also as environment variables
    private static final Set<String> LOCATION_KEYS = new HashSet<>(Arrays.asList(
            "springconfigimport", "springconfiglocation", "springconfigadditionallocation"));
    // The same as arguments, system properties and environment variable assignments in commands
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?:(?:--|-D)spring\\.config\\.(?:import|location|additional-location)|" +
            "\\bSPRING_CONFIG_(?:IMPORT|LOCATION|ADDITIONAL_?LOCATION))=" +
            "(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'<>;&|`()]+))");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern OTHER_SCHEME = Pattern.compile("[a-zA-Z][\\w+.*-]+:.*");
    private static final Pattern DRIVE = Pattern.compile("[a-zA-Z]:/");
    private static final AnnotationMatcher PROPERTY_SOURCE =
            new AnnotationMatcher("@org.springframework.context.annotation.PropertySource");
    private static final AnnotationMatcher TEST_PROPERTY_SOURCE =
            new AnnotationMatcher("@org.springframework.test.context.TestPropertySource");

    private final Set<Path> inSourceSets = new HashSet<>();
    private final Set<Path> builds = new HashSet<>();
    private final Set<Path> springBootBuilds = new HashSet<>();
    private final List<Location> locations = new ArrayList<>();
    private final Map<Path, Boolean> discovered = new HashMap<>();
    private @Nullable List<Glob> globs;

    /**
     * A scanner that records what each source file says about the Spring configuration files of the repository.
     */
    public TreeVisitor<?, ExecutionContext> getScanner() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof Yaml.Documents || sourceFile instanceof Properties.File ||
                       sourceFile instanceof JavaSourceFile || sourceFile instanceof PlainText ||
                       isBuildFile(sourceFile.getSourcePath()) ||
                       DOCKERFILE.matcher(fileName(sourceFile.getSourcePath())).find();
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile && isAcceptable((SourceFile) tree, ctx)) {
                    scan((SourceFile) tree, ctx);
                }
                return tree;
            }
        };
    }

    /**
     * Whether a source file is a YAML or properties file in a source set, or one the scan found to be Spring
     * configuration.
     */
    public boolean isSpringConfigFile(SourceFile sourceFile) {
        return (sourceFile instanceof Yaml.Documents || sourceFile instanceof Properties.File) &&
               (sourceFile.getMarkers().findFirst(SourceSet.class).isPresent() ||
                isSpringConfigFile(sourceFile.getSourcePath()));
    }

    /**
     * Whether the scan found the YAML or properties file at a source path to be Spring configuration.
     */
    public boolean isSpringConfigFile(Path sourcePath) {
        return inSourceSets.contains(sourcePath) || discovered.computeIfAbsent(sourcePath, this::discover);
    }

    private void scan(SourceFile source, ExecutionContext ctx) {
        globs = null;
        discovered.clear();
        Path path = source.getSourcePath();
        if (source instanceof Yaml.Documents || source instanceof Properties.File) {
            if (source.getMarkers().findFirst(SourceSet.class).isPresent()) {
                inSourceSets.add(path);
            }
            if (source instanceof Yaml.Documents) {
                scanYaml((Yaml.Documents) source, ctx);
            } else {
                scanProperties((Properties.File) source);
            }
        } else if (isBuildFile(path)) {
            builds.add(parent(path));
            if (usesSpringBoot(source)) {
                springBootBuilds.add(parent(path));
            }
            // Like the arguments of the Spring Boot plugin's run goal or task
            addAssignments(path, source.printAll());
        } else if (source instanceof JavaSourceFile) {
            if (usesPropertySource().visit(source, ctx) != source) {
                scanJava((JavaSourceFile) source, ctx);
            }
        } else if (DOCKERFILE.matcher(fileName(path)).find()) {
            scanDockerfile(source);
        } else if (source instanceof PlainText) {
            // Like shell scripts and .env files
            addAssignments(path, ((PlainText) source).getText());
        }
    }

    private boolean discover(Path sourcePath) {
        if (isSpringBootDefault(sourcePath)) {
            return true;
        }
        for (Glob glob : globs()) {
            if (glob.matches(sourcePath)) {
                return true;
            }
        }
        return false;
    }

    private void scanYaml(Yaml.Documents documents, ExecutionContext ctx) {
        Path source = documents.getSourcePath();
        new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                String key = uniform(entry.getKey().getValue());
                if ((key.endsWith("import") || key.endsWith("location")) &&
                    (LOCATION_KEYS.contains(key) || LOCATION_KEYS.contains(uniform(propertyPath())))) {
                    for (String value : scalars(entry.getValue())) {
                        add(source, value, null);
                    }
                }
                return super.visitMappingEntry(entry, ctx);
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                // The `name` and `value` of an environment variable of a Kubernetes container
                String name = scalar(mapping, "name");
                String value = scalar(mapping, "value");
                if (name != null && value != null && LOCATION_KEYS.contains(uniform(name))) {
                    add(source, value, null);
                }
                return super.visitMapping(mapping, ctx);
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                addAssignments(source, scalar.getValue());
                return scalar;
            }

            private String propertyPath() {
                StringBuilder path = new StringBuilder();
                for (Cursor c = getCursor(); c != null; c = c.getParent()) {
                    if (c.getValue() instanceof Yaml.Mapping.Entry) {
                        path.insert(0, ((Yaml.Mapping.Entry) c.getValue()).getKey().getValue() + '.');
                    }
                }
                return path.toString();
            }
        }.visit(documents, ctx);
    }

    private void scanProperties(Properties.File file) {
        for (Properties.Content content : file.getContent()) {
            if (content instanceof Properties.Entry) {
                Properties.Entry entry = (Properties.Entry) content;
                // Backslashes escape characters like `:` in properties files
                String value = entry.getValue().getText().replaceAll("\\\\(.)", "$1");
                if (LOCATION_KEYS.contains(uniform(entry.getKey()))) {
                    add(file.getSourcePath(), value, null);
                }
                addAssignments(file.getSourcePath(), value);
            }
        }
    }

    private void scanJava(JavaSourceFile cu, ExecutionContext ctx) {
        String packagePath = cu.getPackageDeclaration() == null ? "" :
                cu.getPackageDeclaration().getPackageName().replace('.', '/');
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                boolean test = TEST_PROPERTY_SOURCE.matches(a);
                if ((test || PROPERTY_SOURCE.matches(a)) && a.getArguments() != null) {
                    for (Expression argument : a.getArguments()) {
                        if (argument instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) argument;
                            String name = assignment.getVariable() instanceof J.Identifier ?
                                    ((J.Identifier) assignment.getVariable()).getSimpleName() : "";
                            if (!"value".equals(name) && !"locations".equals(name)) {
                                continue;
                            }
                            argument = assignment.getAssignment();
                        }
                        for (String location : strings(argument)) {
                            // Without a prefix, these are classpath resources, relative to the package of a test
                            add(cu.getSourcePath(), location, test ? packagePath : "");
                        }
                    }
                }
                return a;
            }
        }.visit(cu, ctx);
    }

    private void scanDockerfile(SourceFile dockerfile) {
        String text = dockerfile instanceof PlainText ? ((PlainText) dockerfile).getText() : dockerfile.printAll();
        String workdir = "";
        List<List<String>> copies = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (String line : text.replaceAll("\\\\\\r?\\n", " ").split("\\r?\\n")) {
            String[] words = line.trim().split("\\s+");
            String instruction = words[0].toUpperCase(Locale.ROOT);
            if ("FROM".equals(instruction)) {
                workdir = "";
            } else if ("WORKDIR".equals(instruction) && words.length > 1) {
                workdir = imagePath(workdir, unquote(words[1]));
            } else if (("COPY".equals(instruction) || "ADD".equals(instruction)) && !line.contains("--from")) {
                List<String> copy = copyArguments(line);
                if (copy.size() > 1) {
                    copy.set(copy.size() - 1, imagePath(workdir, copy.get(copy.size() - 1)));
                    copies.add(copy);
                }
            } else if ("ENV".equals(instruction) && words.length == 3 && LOCATION_KEYS.contains(uniform(words[1]))) {
                // The legacy `ENV key value` form
                values.add(words[2]);
            }
            values.addAll(assignments(line));
        }
        for (String value : values) {
            for (String item : value.split(",")) {
                for (String location : copied(item.trim(), workdir, copies)) {
                    add(dockerfile.getSourcePath(), location, null);
                }
            }
        }
    }

    /**
     * Map a location in the image to the files that COPY or ADD put there, relative to the build context.
     */
    private static List<String> copied(String location, String workdir, List<List<String>> copies) {
        String path = unquote(location).replaceFirst("^optional:", "").replaceFirst("^file:", "");
        if (path.startsWith("classpath") || OTHER_SCHEME.matcher(path).matches()) {
            return singletonList(location);
        }
        boolean directory = path.endsWith("/");
        String image = imagePath(workdir, path);
        List<String> files = new ArrayList<>();
        for (List<String> copy : copies) {
            String destination = copy.get(copy.size() - 1);
            String rest = image.equals(destination) ? "" :
                    image.startsWith(destination + "/") || destination.isEmpty() ?
                            image.substring(destination.isEmpty() ? 0 : destination.length() + 1) : null;
            if (rest == null) {
                continue;
            }
            for (String source : copy.subList(0, copy.size() - 1)) {
                String from = normalize(source);
                if (from == null) {
                    continue;
                }
                // A copied directory, whose contents are at the destination
                files.add("file:./" + join(from, rest) + (directory ? "/" : ""));
                String name = from.substring(from.lastIndexOf('/') + 1);
                if (rest.isEmpty() && directory && CONFIG_NAME.matcher(name).matches()) {
                    files.add("file:./" + from);
                } else if (!directory && !rest.contains("/") && StringUtils.matchesGlob(rest, name)) {
                    // A copied file, or one of the files a wildcard copied
                    files.add("file:./" + join(from.substring(0, Math.max(0, from.lastIndexOf('/'))), rest));
                }
            }
        }
        return files.isEmpty() ? singletonList(location) : files;
    }

    private void add(Path source, String value, @Nullable String classpathRoot) {
        locations.add(new Location(source, value, classpathRoot));
    }

    private void addAssignments(Path source, String text) {
        for (String value : assignments(text)) {
            add(source, value, null);
        }
    }

    private List<Glob> globs() {
        if (globs == null) {
            List<Glob> resolved = new ArrayList<>();
            for (Location location : locations) {
                for (String item : location.getValue().split(",")) {
                    addGlobs(resolved, location, unquote(item.trim()));
                }
            }
            globs = resolved;
        }
        return globs;
    }

    private void addGlobs(List<Glob> resolved, Location location, String item) {
        String classpathRoot = location.getClasspathRoot();
        String path = item.startsWith("optional:") ? item.substring("optional:".length()) : item;
        if (path.startsWith("classpath:") || path.startsWith("classpath*:")) {
            path = path.substring(path.indexOf(':') + 1);
            classpathRoot = "";
        } else if (path.startsWith("file:")) {
            path = path.substring("file:".length());
            classpathRoot = null;
        } else if (OTHER_SCHEME.matcher(path).matches()) {
            // Like configtree: or http:
            return;
        }
        // Drop an extension hint, as in `file:/etc/config/myconfig[.yaml]`
        path = wildcards(path.replace('\\', '/')).replaceFirst("\\[\\.\\w+]$", "");
        boolean directory = path.endsWith("/");
        if (!directory && !hasLiteralName(path)) {
            return;
        }
        String module = unix(moduleOf(location.getSource()));
        if (classpathRoot != null) {
            // A classpath resource outside the source sets can only be on the classpath of its own module
            String resource = path.startsWith("/") ? path : join(classpathRoot, path);
            String glob = normalize(join(join(module, "**"), resource));
            if (glob != null) {
                resolved.add(new Glob(glob, directory));
            }
        } else if (path.startsWith("/") || path.startsWith("**") || DRIVE.matcher(path).lookingAt()) {
            // Where the application runs, as in a container, an absolute path may hold a copy of the file at the
            // same path in the repository
            String glob = normalize(path.startsWith("**") ? path : join("**", path.replaceFirst("^[a-zA-Z]:", "")));
            if (glob != null) {
                resolved.add(new Glob(glob, directory));
            }
        } else {
            // Relative to the file that names it, or to the working directory of the module or the repository
            for (String base : new LinkedHashSet<>(Arrays.asList(unix(parent(location.getSource())), module, ""))) {
                String glob = normalize(join(base, path));
                if (glob != null) {
                    resolved.add(new Glob(glob, directory));
                }
            }
        }
    }

    /**
     * Spring Boot loads application and bootstrap files from its working directory, the {@code config/} directory
     * in it, and the directories in that.
     */
    private boolean isSpringBootDefault(Path sourcePath) {
        if (!CONFIG_NAME.matcher(fileName(sourcePath)).matches()) {
            return false;
        }
        Path dir = parent(sourcePath);
        Path up = dir == null ? null : parent(dir);
        return springBootBuilds.contains(dir) ||
               isConfigDir(dir) && springBootBuilds.contains(up) ||
               up != null && isConfigDir(up) && springBootBuilds.contains(parent(up));
    }

    private Path moduleOf(Path sourcePath) {
        for (Path dir = parent(sourcePath); dir != null; dir = parent(dir)) {
            if (builds.contains(dir)) {
                return dir;
            }
        }
        return ROOT;
    }

    @Value
    private static class Location {
        Path source;
        String value;

        /**
         * The directory a classpath resource without a prefix is relative to, or null when such a location is a file.
         */
        @Nullable
        String classpathRoot;
    }

    @Value
    private static class Glob {
        String pattern;

        /**
         * Whether the pattern is of a directory, in which the application and bootstrap files are Spring configuration.
         */
        boolean directory;

        boolean matches(Path sourcePath) {
            if (directory) {
                Path dir = parent(sourcePath);
                return dir != null && CONFIG_NAME.matcher(fileName(sourcePath)).matches() &&
                       PathUtils.matchesGlob(dir, pattern);
            }
            return PathUtils.matchesGlob(sourcePath, pattern);
        }
    }

    private static TreeVisitor<?, ExecutionContext> usesPropertySource() {
        return Preconditions.or(
                new UsesType<>("org.springframework.context.annotation.PropertySource", false),
                new UsesType<>("org.springframework.test.context.TestPropertySource", false));
    }

    private static boolean usesSpringBoot(SourceFile build) {
        MavenResolutionResult maven = build.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
        if (maven != null) {
            Pom pom = maven.getPom().getRequested();
            return !maven.findDependencies(SPRING_BOOT, "*", null).isEmpty() ||
                   pom.getParent() != null && SPRING_BOOT.equals(pom.getParent().getGroupId()) ||
                   pom.getDependencyManagement().stream().anyMatch(dependency ->
                           SPRING_BOOT.equals(dependency.getGroupId()) &&
                           "spring-boot-dependencies".equals(dependency.getArtifactId()));
        }
        GradleProject gradle = build.getMarkers().findFirst(GradleProject.class).orElse(null);
        return gradle != null &&
               (gradle.getPlugins().stream().anyMatch(plugin -> SPRING_BOOT.equals(plugin.getId()) ||
                        plugin.getFullyQualifiedClassName().startsWith(SPRING_BOOT + ".")) ||
                gradle.getConfigurations().stream().anyMatch(configuration -> configuration.getRequested().stream()
                        .anyMatch(dependency -> SPRING_BOOT.equals(dependency.getGroupId()))));
    }

    private static List<String> scalars(Yaml.Block block) {
        if (block instanceof Yaml.Scalar) {
            return singletonList(((Yaml.Scalar) block).getValue());
        }
        List<String> values = new ArrayList<>();
        if (block instanceof Yaml.Sequence) {
            for (Yaml.Sequence.Entry entry : ((Yaml.Sequence) block).getEntries()) {
                if (entry.getBlock() instanceof Yaml.Scalar) {
                    values.add(((Yaml.Scalar) entry.getBlock()).getValue());
                }
            }
        }
        return values;
    }

    private static @Nullable String scalar(Yaml.Mapping mapping, String key) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if (key.equals(entry.getKey().getValue()) && entry.getValue() instanceof Yaml.Scalar) {
                return ((Yaml.Scalar) entry.getValue()).getValue();
            }
        }
        return null;
    }

    private static List<String> strings(Expression expression) {
        List<Expression> elements = expression instanceof J.NewArray ? ((J.NewArray) expression).getInitializer() :
                expression instanceof K.ListLiteral ? ((K.ListLiteral) expression).getElements() :
                        singletonList(expression);
        List<String> strings = new ArrayList<>();
        if (elements != null) {
            for (Expression element : elements) {
                if (element instanceof J.Literal && ((J.Literal) element).getValue() instanceof String) {
                    strings.add((String) ((J.Literal) element).getValue());
                }
            }
        }
        return strings;
    }

    private static List<String> assignments(String text) {
        if (!text.contains("pring.config.") && !text.contains("SPRING_CONFIG_")) {
            return emptyList();
        }
        List<String> values = new ArrayList<>();
        Matcher matcher = ASSIGNMENT.matcher(text);
        while (matcher.find()) {
            values.add(matcher.group(1) != null ? matcher.group(1) :
                    matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        }
        return values;
    }

    private static List<String> copyArguments(String line) {
        // Without the instruction and flags like --chown
        String arguments = line.trim().replaceFirst("^\\S+", "").replaceAll("(^|\\s)--\\S+", " ").trim();
        if (!arguments.startsWith("[")) {
            return new ArrayList<>(Arrays.asList(arguments.split("\\s+")));
        }
        List<String> quoted = new ArrayList<>();
        Matcher matcher = QUOTED.matcher(arguments);
        while (matcher.find()) {
            quoted.add(matcher.group(1));
        }
        return quoted;
    }

    /**
     * A path in an image, as resolved against the working directory, without its leading and trailing slashes.
     */
    private static String imagePath(String workdir, String path) {
        String resolved = normalize(path.startsWith("/") ? path : join(workdir, path));
        return resolved == null ? "" : resolved;
    }

    /**
     * {@code ${...}} placeholders become wildcards, and a placeholder that is a whole path segment may stand for
     * several of them.
     */
    private static String wildcards(String path) {
        StringBuilder marked = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            if (path.startsWith("${", i)) {
                int depth = 0;
                int j = i + 1;
                for (; j < path.length(); j++) {
                    if (path.charAt(j) == '{') {
                        depth++;
                    } else if (path.charAt(j) == '}' && --depth == 0) {
                        break;
                    }
                }
                marked.append('\0');
                i = j;
            } else {
                marked.append(path.charAt(i));
            }
        }
        return marked.toString().replaceAll("(^|/)\0+(?=/|$)", "$1**").replace('\0', '*');
    }

    /**
     * Whether the file name names a file rather than any file, as a bare placeholder or {@code *.yml} would.
     */
    private static boolean hasLiteralName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return !(dot < 0 ? name : name.substring(0, dot)).replace("*", "").isEmpty();
    }

    /**
     * Resolves {@code .} and {@code ..} segments, dropping leading and trailing slashes, or null when the path leaves
     * the repository.
     */
    private static @Nullable String normalize(String path) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                if (segments.isEmpty() || "**".equals(segments.peekLast())) {
                    return null;
                }
                segments.removeLast();
            } else if (!segment.isEmpty() && !".".equals(segment)) {
                segments.add(segment);
            }
        }
        return String.join("/", segments);
    }

    private static String join(String parent, String child) {
        return parent.isEmpty() ? child : child.isEmpty() ? parent : parent + "/" + child;
    }

    private static String uniform(String key) {
        return key.toLowerCase(Locale.ROOT).replace(".", "").replace("-", "").replace("_", "");
    }

    private static String unquote(String value) {
        return value.length() > 1 && (value.startsWith("\"") && value.endsWith("\"") ||
                                      value.startsWith("'") && value.endsWith("'")) ?
                value.substring(1, value.length() - 1) : value;
    }

    private static boolean isBuildFile(Path sourcePath) {
        String name = fileName(sourcePath);
        return "pom.xml".equals(name) || "build.gradle".equals(name) || "build.gradle.kts".equals(name);
    }

    private static boolean isConfigDir(@Nullable Path dir) {
        return dir != null && "config".equals(fileName(dir));
    }

    private static @Nullable Path parent(Path path) {
        Path parent = path.getParent();
        return parent != null ? parent : path.toString().isEmpty() ? null : ROOT;
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }

    private static String unix(@Nullable Path path) {
        return path == null ? "" : PathUtils.separatorsToUnix(path.toString());
    }
}

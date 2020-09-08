/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.spring.boot2;

import org.openrewrite.AutoConfigure;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.internal.StringUtils.capitalize;

/**
 * Notes on ValueToConfigurationProperties
 * <p>
 * 1. Scanning phase: Visit class fields, constructors for @Value annotations create a tree of @Value annotation contents.
 * Given @Values containing these property paths:
 * app.config.bar, app.config.foo, screen.resolution.horizontal, screen.resolution.vertical, screen.refresh-rate
 * The resulting tree should be:
 * <pre>
 *                root
 *            /         \
 *          app         screen
 *        /           /       \
 *    config     resolution    refreshRate
 *    /        /          \
 * bar     horizontal     vertical
 * </pre>
 * Store list of classes where every field is @Value annotated as it can be reused instead of generating a new class
 * Store a list of any existing @ConfigurationProperties classes
 * Record list of fields whose names don't match the last piece of their @Value annotations.
 * Leaf nodes of tree have links back to their original appearance(s)
 * <p>
 * 1.b.:  Config Class Generation:
 * For each subtree where there is not an existing ConfigurationProperties class, create a new (empty) ConfigurationProperties class
 * Any new classes should be placed adjacent in the source tree to the Spring Application class
 * <p>
 * 2. Config Class Update Phase:
 * Go through the config classes and create fields, getters, setters, corresponding to each node of the tree
 * <p>
 * 3. Reference Update phase:
 * Go through ALL classes and anywhere anything @Value annotated appears, replace it with the corresponding @ConfigurationProperties type.
 * May involve collapsing multiple arguments into a single argument, updating references to those arguments
 * <p>
 * Edge cases to remember:
 * Existing field doesn't have the same name as its @Value annotation would imply
 * There are already @ConfigurationProperties classes for some prefixes
 * Map of prefix to existing class?
 * Constructors or methods with @Value annotated arguments
 * One ConfigurationProperties argument may replace many @Value annotated arguments
 * Pre-existing @ConfigurationProperties annotated class with no prefix?
 */
@AutoConfigure
public class ValueToConfigurationProperties extends JavaRefactorVisitor {

    private static final String valueAnnotationSignature = "@org.springframework.beans.factory.annotation.Value";
    private static final String configurationPropertiesFqn = "org.springframework.boot.context.properties.ConfigurationProperties";
    private static final String configurationPropertiesSignature = "@" + configurationPropertiesFqn;
    private static final JavaType.Class configurationPropertiesAnnotationType = JavaType.Class.build(
            configurationPropertiesFqn,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.singletonList(JavaType.Class.build("java.lang.annotation.Annotation")),
            Collections.emptyList(),
            null);
    private static final String springBootApplicationSignature = "@org.springframework.boot.autoconfigure.SpringBootApplication";

    // Visible for testing
    PrefixParentNode prefixTree = PrefixTree.build();
    J.CompilationUnit springBootApplication;
    boolean configClassesGenerated = false;
    JavaParser jp;

    public ValueToConfigurationProperties() {
        setCursoringOn();
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        if (configClassesGenerated && springBootApplication != null) {
            andThen(new UpdateConfigurationPropertiesClasses());
        }
        return super.visitCompilationUnit(cu);
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        if (!configClassesGenerated) {
            classDecl.getFields()
                    .forEach(prefixTree::put);
            classDecl.getMethods()
                    .forEach(prefixTree::put);
            // Any generated config classes will adopt the package of the Spring Boot Application class
            // and be placed adjacent to it in the source tree
            if (classDecl.findAnnotations(springBootApplicationSignature).size() > 0) {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                assert cu != null;
                springBootApplication = cu;
                jp = cu.buildParser();
            }
        }
        return super.visitClassDecl(classDecl);
    }

    private static String toConfigPropsClassName(List<String> prefixPaths) {
        return prefixPaths.stream().map(StringUtils::capitalize)
                .collect(Collectors.joining("")) + "Configuration";
    }

    @Override
    public Collection<SourceFile> generate() {
        if(!configClassesGenerated && springBootApplication != null) {
            configClassesGenerated = true;
            return prefixTree.getLongestCommonPrefixes().stream().map(commonPrefix -> {
                String className = toConfigPropsClassName(commonPrefix);
                String peckage = (springBootApplication.getPackageDecl() == null) ? "" : springBootApplication.getPackageDecl().printTrimmed() + ";\n\n";
                String newClassText = peckage +
                        "import org.springframework.boot.context.properties.ConfigurationProperties;\n\n" +
                        "@ConfigurationProperties(\"" + String.join(".", commonPrefix) + "\")\n" +
                        "public class " + className + " {\n" +
                        "}\n";
                Path sourcePath = Paths.get(springBootApplication.getSourcePath()).toAbsolutePath().getParent().resolve(className + ".java");

                J.CompilationUnit compilationUnit = fillConfigurationPropertiesTypeAttribution(
                        jp.reset().parse(newClassText)
                                .get(0)
                                .withSourcePath(sourcePath.toString()));

                return compilationUnit;
            }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
    private static final J.CompilationUnit fillConfigurationPropertiesTypeAttribution(J.CompilationUnit cu) {
        return (J.CompilationUnit) new FillTypeAttributions(new JavaType.Class[]{configurationPropertiesAnnotationType}).visitCompilationUnit(cu);
    }

    private class UpdateConfigurationPropertiesClasses extends JavaRefactorVisitor {
        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl cd = refactor(classDecl, super::visitClassDecl);
            List<J.Annotation> configPropsAnnotations = cd.findAnnotations(configurationPropertiesSignature);
            if (configPropsAnnotations.size() > 0) {
                J.Annotation configPropsAnnotation = configPropsAnnotations.get(0);
                if (configPropsAnnotation.getArgs() == null) {
                    return cd;
                }
                String configPropsPrefix = (String) ((J.Literal) configPropsAnnotation.getArgs().getArgs().get(0)).getValue();
                if (configPropsPrefix == null) {
                    return cd;
                }
                PrefixParentNode treeForConfigPropsClass = (PrefixParentNode)prefixTree.get(configPropsPrefix);
                for(PrefixTree pt : treeForConfigPropsClass.children.values()) {
                    generateClassesFieldsGettersSetters(cd, pt);
                }
            }
            return cd;
        }

        /**
         * Based on the contents of the supplied PrefixTree, create inner classes, fields, getters, and setters
         * Given a class declaration like:
         * class Example {}
         * and a PrefixTree like this, assuming all properties are Strings
         *       root
         *        |
         *       app
         *        |
         *     config
         *    /     \
         *  foo     bar
         *
         * Example will end up looking similar to the following (whitespace and ordering of fields/methods not guaranteed):S
         *
         * class Example {
         *     private AppConfig appConfig;
         *     public AppConfig getAppConfig() { return appConfig; }
         *     public void setAppConfig(AppConfig value) { appConfig = value; }
         *     public static class AppConfig {
         *         private String foo;
         *         public String getFoo() { return foo; }
         *         public void setFoo(String value) { foo = value; }
         *         private String bar;
         *         public String getBar() { return bar; }
         *         public void setBar(String value) { bar = value; }
         *     }
         * }
         */
        private void generateClassesFieldsGettersSetters(J.ClassDecl cd, PrefixTree pt) {
            if(pt instanceof PrefixTerminalNode) {
                PrefixTerminalNode node = (PrefixTerminalNode) pt;
                String fieldName = node.name;
                String fieldTypeExpr = node.getType().toTypeTree().printTrimmed();
                andThen(new AddField.Scoped(cd,
                        Collections.singletonList(new J.Modifier.Private(randomId(), EMPTY)),
                        fieldTypeExpr,
                        fieldName,
                        null));
                andThen(new GenerateGetter.Scoped(cd.getType(), fieldName));
                andThen(new GenerateSetter.Scoped(cd.getType(), fieldName));
            } else if(pt instanceof PrefixParentNode) {
                // Search through any inner class declarations that may exist, use existing declaration if one exists
                String fieldName = pt.getName();
                String innerClassName = capitalize(fieldName);
                Optional<J.ClassDecl> maybeInnerClassDecl = cd.getBody().getStatements().stream()
                        .filter(it -> it instanceof J.ClassDecl)
                        .map(J.ClassDecl.class::cast)
                        .filter(it -> it.getSimpleName().equals(innerClassName))
                        .findAny();

                J.ClassDecl innerClassDecl;
                if(maybeInnerClassDecl.isPresent()) {
                    innerClassDecl = maybeInnerClassDecl.get();
                } else {
                    innerClassDecl = treeBuilder.buildInnerClassDeclaration(
                            cd,
                            "public static class " + innerClassName + " {\n}\n");
                    List<J> withNewDecl = cd.getBody().getStatements();
                    withNewDecl.add(innerClassDecl);
                    cd = cd.withBody(cd.getBody().withStatements(withNewDecl));
                    andThen(new AutoFormat(innerClassDecl));
                }
                assert innerClassDecl.getType() != null;
                andThen(new AddField.Scoped(cd,
                        Collections.singletonList(new J.Modifier.Private(randomId(), EMPTY)),
                        innerClassDecl.getType().getFullyQualifiedName(),
                        fieldName,
                        null));
                andThen(new GenerateGetter.Scoped(cd.getType(), fieldName));
                andThen(new GenerateSetter.Scoped(cd.getType(), fieldName));
                // There needs to be a field and getter/setter for the inner class. Any of these may already exist

                // Recurse on any children
                ((PrefixParentNode) pt).children.values().forEach(subTree -> generateClassesFieldsGettersSetters(innerClassDecl, subTree));
            }
        }
    }

    /**
     * Extracts, de-dashes, and camelCases the value string from a @Value annotation
     * Given:   @Value("${app.screen.refresh-rate}")
     * Returns: app.screen.refreshRate
     */
    private static String getValueValue(J.Annotation value) {
        assert value.getArgs() != null;
        String valueValue = (String) ((J.Literal) value.getArgs().getArgs().get(0)).getValue();
        assert valueValue != null;
        valueValue = valueValue.replace("${", "")
                .replace("}", "");
        valueValue = Arrays.stream(valueValue.split("-"))
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(""));
        return Character.toLowerCase(valueValue.charAt(0)) + valueValue.substring(1);
    }

    interface PrefixTree {
        String getName();

        PrefixTree put(List<String> pathSegments, J source);

        PrefixTree get(List<String> pathSegments);

        PrefixParentNode getParent();

        default boolean isRoot() { return getParent() == null; }

        default String getFullPath() {
            if(isRoot()) {
                return "";
            }
            String parentPath;
            if(getParent().isRoot()) {
                return getName();
            } else {
                return getParent().getFullPath() + "." + getName();
            }
        }

        static PrefixParentNode build() {
            return new PrefixParentNode(null, "root");
        }
    }

    /**
     * A node of a PrefixTree with no children of its own.
     * Keeps track of the element or elements which reference it
     */
    public static class PrefixTerminalNode implements PrefixTree {
        final String name;
        final PrefixParentNode parent;
        List<J> originalAppearances = new ArrayList<>();

        public PrefixTerminalNode(PrefixParentNode parent, String name, J originalAppearance) {
            this.name = name;
            this.parent = parent;
            originalAppearances.add(originalAppearance);
        }

        @Override
        public PrefixTree put(List<String> pathSegments, J source) {
            if (pathSegments == null) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            if (source == null) {
                throw new IllegalArgumentException("source may not be null");
            }
            if (pathSegments.size() > 1) {
                throw new IllegalArgumentException("Cannot add new path segment to terminal node");
            }
            originalAppearances.add(source);
            return this;
        }

        @Override
        public PrefixTree get(List<String> pathSegments) {
            if (pathSegments == null) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            if (pathSegments.size() == 0) {
                return this;
            } else if (pathSegments.size() == 1 && pathSegments.get(0).equals(name)) {
                return this;
            } else {
                throw new IllegalArgumentException(
                        "Terminal node \"" + name + "\" does not match requested path \"" +
                                String.join(".", pathSegments) + "\"");
            }
        }

        @Override
        public PrefixParentNode getParent() {
            return parent;
        }

        @Override
        public String getName() {
            return name;
        }

        public JavaType getType() {
            J originalAppearance = originalAppearances.get(0);
            if(originalAppearance instanceof J.VariableDecls) {
                J.VariableDecls feild = (J.VariableDecls) originalAppearance;
                return feild.getVars().get(0).getType();
            } else if(originalAppearance instanceof J.MethodDecl) {
                J.MethodDecl method = (J.MethodDecl) originalAppearance;
                Optional<JavaType> param = method.getParams().getParams().stream()
                        .filter(it -> it instanceof J.VariableDecls)
                        .map(J.VariableDecls.class::cast)
                        .filter(it -> {
                            List<J.Annotation> valueAnnotations = it.findAnnotations(valueAnnotationSignature);
                            if(valueAnnotations.size() == 0) {
                                return false;
                            }
                            return getFullPath().equals(getValueValue(valueAnnotations.get(0)));
                        })
                        .map( it -> it.getVars().get(0).getType())
                        .findAny();
                if(param.isPresent()) {
                    return param.get();
                }
            }
            throw new RuntimeException("Could not determine type of \"" + originalAppearance.print() + "\"");
        }
    }

    /**
     * A root or intermediate node of a PrefixTree. Has no data except via its child nodes. Get an instance via PrefixTree.build()
     */
    public static class PrefixParentNode implements PrefixTree {
        final String name;
        final Map<String, PrefixTree> children = new HashMap<>();
        final PrefixParentNode parent;

        private PrefixParentNode(PrefixParentNode parent, String name) {
            this.name = name;
            this.parent = parent;
        }

        PrefixTree buildChild(List<String> pathSegments, J source) {
            if (pathSegments.size() == 0) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            String nodeName = pathSegments.get(0);
            List<String> remainingSegments = pathSegments.subList(1, pathSegments.size());
            if (remainingSegments.size() == 0) {
                return new PrefixTerminalNode(this, nodeName, source);
            } else {
                PrefixParentNode intermediateNode = new PrefixParentNode(this, nodeName);
                PrefixTree child = intermediateNode.buildChild(remainingSegments, source);
                intermediateNode.children.put(child.getName(), child);
                return intermediateNode;
            }
        }

        void put(J.VariableDecls field) {
            List<J.Annotation> valueAnnotations = field.findAnnotations(valueAnnotationSignature);
            if (valueAnnotations.size() == 0) {
                return;
            }
            J.Annotation valueAnnotation = valueAnnotations.get(0);
            String path = getValueValue(valueAnnotation);
            List<String> pathSegments = Arrays.asList(path.split("\\."));
            put(pathSegments, field);
        }

        void put(J.MethodDecl methodDecl) {
            Optional<J.Annotation> maybeAnnotation = methodDecl.getParams().getParams().stream()
                    .filter(decl -> decl instanceof J.VariableDecls)
                    .map(J.VariableDecls.class::cast)
                    .map(decl -> decl.findAnnotations(valueAnnotationSignature))
                    .filter(it -> it != null && it.size() > 0)
                    .map(annotations -> annotations.get(0))
                    .findAny();

            if (maybeAnnotation.isPresent()) {
                List<String> pathSegments = Arrays.asList(getValueValue(maybeAnnotation.get()).split("\\."));
                put(pathSegments, methodDecl);
            }
        }

        @Override
        public PrefixTree put(List<String> pathSegments, J source) {
            if (pathSegments == null) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            if (pathSegments.size() == 0) {
                throw new IllegalArgumentException("pathSegments may not be empty");
            }
            String nodeName = pathSegments.get(0);

            if (children.containsKey(nodeName)) {
                PrefixTree existingNode = children.get(nodeName);
                existingNode.put(pathSegments.subList(1, pathSegments.size()), source);
            } else {
                children.put(nodeName, buildChild(pathSegments, source));
            }
            return this;
        }

        public PrefixTree get(String path) {
            return get(Arrays.asList(path.split("\\.")));
        }

        @Override
        public PrefixTree get(List<String> pathSegments) {
            if (pathSegments == null) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            if (pathSegments.size() == 0) {
                return this;
            }
            String nodeName = pathSegments.get(0);
            List<String> remainingSegments = pathSegments.subList(1, pathSegments.size());
            if (children.containsKey(nodeName)) {
                return children.get(nodeName).get(remainingSegments);
            } else {
                return null;
            }
        }

        @Override
        public PrefixParentNode getParent() {
            return parent;
        }

        @Override
        public String getName() {
            return name;
        }

        /**
         * Return the longest paths down each branch of the tree for which there is exactly one non-terminal child
         * or no non-terminal children and any number of terminal children.
         *
         * So for a tree like:
         *                  root
         *              /         \
         *             app         screen
         *          /            /       \
         *      config    refreshRate   resolution
         *     /     \
         *   foo     bar
         *
         * This will return a list like
         * [app.config, screen]
         */
        public List<List<String>> getLongestCommonPrefixes() {
            List<List<String>> result = new ArrayList<>();
            for (PrefixTree subtree : children.values()) {
                if (subtree instanceof PrefixParentNode) {
                    List<String> intermediate = new ArrayList<>();
                    getUntilTerminalOrMultipleChildren(intermediate, (PrefixParentNode) subtree);
                    result.add(intermediate);
                } else {
                    List<String> root = Collections.singletonList("root");
                    if (!result.contains(root)) {
                        result.add(root);
                    }
                }
            }
            return result;
        }

        private void getUntilTerminalOrMultipleChildren(List<String> resultSoFar, PrefixParentNode parentNode) {
            PrefixTree node = parentNode;
            List<PrefixTree> children;
            do {
                if (!(node instanceof PrefixParentNode)) {
                    break;
                }
                resultSoFar.add(node.getName());
                children = new ArrayList<>(((PrefixParentNode) node).children.values());
                node = children.get(0);
            } while (children.size() == 1 && children.get(0) instanceof PrefixParentNode);
        }
    }
}

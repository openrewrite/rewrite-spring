package org.openrewrite.spring.boot2;

import org.openrewrite.AutoConfigure;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Notes on ValueToConfigurationProperties
 *
 * 1. Scanning phase: Visit class fields, constructors for @Value annotations create a tree of @Value annotation contents.
 *     Given @Values containing these property paths:
 *         app.config.bar, app.config.foo, screen.resolution.horizontal, screen.resolution.vertical, screen.refresh-rate
 *     The resulting tree should be:
 *                                    root
 *                                 /         \
 *                             app            screen
 *                            /              /       \
 *                       config        resolution    refreshRate
 *                        /           /          \
 *                      bar     horizontal     vertical
 *
 *     Store list of classes where every field is @Value annotated as it can be reused instead of generating a new class
 *     Store a list of any existing @ConfigurationProperties classes
 *     Record list of fields whose names don't match the last piece of their @Value annotations.
 *         Leaf nodes of tree have links back to their original appearance(s)
 *
 *     1.b.:  Config Class Generation:
 *         For each subtree where there is not an existing ConfigurationProperties class, create a new (empty) ConfigurationProperties class
 *         Any new classes should be placed adjacent in the source tree to the Spring Application class
 *
 * 2. Config Class Update Phase:
 *     Go through the config classes and create fields, getters, setters, corresponding to each node of the tree
 *
 * 3. Reference Update phase:
 *     Go through ALL classes and anywhere anything @Value annotated appears, replace it with the corresponding @ConfigurationProperties type.
 *     May involve collapsing multiple arguments into a single argument, updating references to those arguments
 *
 * Edge cases to remember:
 *     Existing field doesn't have the same name as its @Value annotation would imply
 *     There are already @ConfigurationProperties classes for some prefixes
 *         Map of prefix to existing class?
 *     Constructors or methods with @Value annotated arguments
 *         One ConfigurationProperties argument may replace many @Value annotated arguments
 *     Pre-existing @ConfigurationProperties annotated class with no prefix?
 */
@AutoConfigure
public class ValueToConfigurationProperties2 extends JavaRefactorVisitor {
    private static final String valueAnnotationSignature = "@org.springframework.beans.factory.annotation.Value";
    private static final String configurationPropertiesSignature = "@org.springframework.boot.context.properties.ConfigurationProperties";
    private static final String springBootApplicationSignature = "@org.springframework.boot.autoconfigure.SpringBootApplication";

    // Visible for testing
    PrefixParentNode prefixTree = new PrefixParentNode("root");
    J.Package peckage = null;
    private JavaParser jp = null;
    boolean firstPhaseComplete = false;
    boolean shouldRequestAdditionalCycle = false;
    public ValueToConfigurationProperties2() {
        setCursoringOn();
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        if(!firstPhaseComplete) {
            classDecl.getFields().stream()
                    .forEach(prefixTree::put);
            classDecl.getMethods().stream()
                    .forEach(prefixTree::put);
            // Any generated config classes will adopt the package of the Spring Boot Application class
            // and be placed adjacent to it in the source tree
            if (classDecl.findAnnotations(springBootApplicationSignature).size() > 0) {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                assert cu != null;
                peckage = cu.getPackageDecl();
                jp = JavaParser.fromJavaVersion()
                        .styles(cu.getStyles())
                        .build();
                shouldRequestAdditionalCycle = true;
            }
        } else {
            shouldRequestAdditionalCycle = false;
        }
        return super.visitClassDecl(classDecl);
    }

    @Override
    public void onCycleComplete() {
        if(peckage != null && jp != null) {
            firstPhaseComplete = true;
            andThen(new GenerateConfigurationPropertiesClasses(prefixTree, peckage, jp));
        }
    }

    @Override
    public boolean requestsAdditionalCycle() {
        return shouldRequestAdditionalCycle;
    }

    private static String toConfigPropsClassName(List<String> prefixPaths) {
        return prefixPaths.stream().map(StringUtils::capitalize).collect(Collectors.joining("")) + "Configuration";
    }

    public static class GenerateConfigurationPropertiesClasses extends JavaRefactorVisitor {
        final PrefixParentNode prefixTree;
        final J.Package peckage;
        final JavaParser jp;
        public GenerateConfigurationPropertiesClasses(PrefixParentNode prefixTree, J.Package peckage, JavaParser jp) {
            this.prefixTree = prefixTree;
            this.peckage = peckage;
            this.jp = jp;
        }

        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl cd = refactor(classDecl, super::visitClassDecl);
            List<J.Annotation> configPropsAnnotations = cd.findAnnotations(configurationPropertiesSignature);
            if(configPropsAnnotations.size() > 0) {
                J.Annotation configPropsAnnotation = configPropsAnnotations.get(0);
                if(configPropsAnnotation.getArgs() == null) {
                    return cd;
                }
                String configPropsPrefix = (String) ((J.Literal) configPropsAnnotation.getArgs().getArgs().get(0)).getValue();
                if(configPropsPrefix == null) {
                    return cd;
                }
                PrefixTree treeForConfigPropsClass = prefixTree.get(configPropsPrefix);
                // TODO: Deal with existing ConfigurationProperties classes

            }
            return cd;
        }

        @Override
        public Collection<J> generate() {
            return prefixTree.getLongestCommonPrefixes().stream().map(commonPrefix -> {
                String className = toConfigPropsClassName(commonPrefix);
                String newClassText = peckage.print() + ";\n\n" +
                        "import org.springframework.boot.context.properties.ConfigurationProperties;\n\n" +
                        "@ConfigurationProperties(\"" + commonPrefix.stream().collect(Collectors.joining(".")) + "\")\n" +
                        "public class " + className + "{\n" +
                        "}\n";
                J.CompilationUnit cu = jp.parse(newClassText).get(0);
                return (J)cu;
            }).collect(Collectors.toList());
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

        static PrefixParentNode build() {
            return new PrefixParentNode("root");
        }
    }

    /**
     * A node of a PrefixTree with no children of its own.
     * Keeps track of the element or elements which reference it
     */
    public static class PrefixTerminalNode implements PrefixTree {
        final String name;
        List<J> originalAppearances = new ArrayList<>();

        public PrefixTerminalNode(String name, J originalAppearance) {
            this.name = name;
            originalAppearances.add(originalAppearance);
        }

        @Override
        public PrefixTree put(List<String> pathSegments, J source) {
            if(pathSegments == null) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            if(source == null) {
                throw new IllegalArgumentException("source may not be null");
            }
            if(pathSegments.size() > 1) {
                throw new IllegalArgumentException("Cannot add new path segment to terminal node");
            }
            originalAppearances.add(source);
            return this;
        }

        @Override
        public PrefixTree get(List<String> pathSegments) {
            if(pathSegments == null) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            if(pathSegments.size() == 0) {
                return this;
            } else if(pathSegments.size() == 1 && pathSegments.get(0).equals(name)) {
                return this;
            } else {
                throw new IllegalArgumentException(
                        "Terminal node \"" + name + "\" does not match requested path \"" +
                                pathSegments.stream().collect(Collectors.joining(".")) + "\"");
            }
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * A root or intermediate node of a PrefixTree. Has no data except via its child nodes. Get an instance via PrefixTree.build()
     */
    public static class PrefixParentNode implements PrefixTree {
        final String name;
        final Map<String, PrefixTree> children = new HashMap<>();

        private PrefixParentNode(String name) {
            this.name = name;
        }
        private PrefixParentNode(String name, PrefixTree child) {
            this.name = name;
            children.put(child.getName(), child);
        }

        public static PrefixTree build(List<String> pathSegments, J source) {
            if(pathSegments.size() == 0) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            String nodeName = pathSegments.get(0);
            List<String> remainingSegments = pathSegments.subList(1, pathSegments.size());
            if(remainingSegments.size() == 0) {
                return new PrefixTerminalNode(nodeName, source);
            } else {
                return new PrefixParentNode(nodeName, build(remainingSegments, source));
            }
        }

        public PrefixTree put(J.VariableDecls field) {
            List<J.Annotation> valueAnnotations = field.findAnnotations(valueAnnotationSignature);
            if(valueAnnotations.size() == 0) {
                return this;
            }
            J.Annotation valueAnnotation = valueAnnotations.get(0);
            String path = getValueValue(valueAnnotation);
            List<String> pathSegments = Arrays.asList(path.split("\\."));
            return put(pathSegments, field);
        }

        public PrefixTree put(J.MethodDecl methodDecl) {
            Optional<J.Annotation> maybeAnnotation = methodDecl.getParams().getParams().stream()
                    .filter(decl -> decl instanceof J.VariableDecls)
                    .map(decl -> (J.VariableDecls) decl)
                    .map(decl -> decl.findAnnotations(valueAnnotationSignature))
                    .filter(it -> it != null && it.size() > 0)
                    .map(annotations -> annotations.get(0))
                    .findAny();

            if(maybeAnnotation.isPresent()) {
                List<String> pathSegments = Arrays.asList(getValueValue(maybeAnnotation.get()).split("\\."));
                return put(pathSegments, methodDecl);
            }
            return this;
        }

        @Override
        public PrefixTree put(List<String> pathSegments, J source) {
            if(pathSegments == null) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            if(pathSegments.size() == 0) {
                throw new IllegalArgumentException("pathSegments may not be empty");
            }
            String nodeName = pathSegments.get(0);

            if(children.containsKey(nodeName)) {
                PrefixTree existingNode = children.get(nodeName);
                existingNode.put(pathSegments.subList(1, pathSegments.size()), source);
            } else {
                children.put(nodeName, build(pathSegments, source));
            }
            return this;
        }

        public PrefixTree get(String path) {
            return get(Arrays.asList(path.split("\\.")));
        }

        @Override
        public PrefixTree get(List<String> pathSegments) {
            if(pathSegments == null) {
                throw new IllegalArgumentException("pathSegments may not be null");
            }
            if(pathSegments.size() == 0) {
                return this;
            }
            String nodeName = pathSegments.get(0);
            List<String> remainingSegments = pathSegments.subList(1, pathSegments.size());
            if(children.containsKey(nodeName)) {
                return children.get(nodeName).get(remainingSegments);
            } else {
                return null;
            }
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
         *           app          screen
         *          /          /          \
         *      config     refreshRate      resolution
         *     /     \
         *   foo     bar
         *
         * This will return a list like
         * [app.config, screen]
         */
        public List<List<String>> getLongestCommonPrefixes() {
            List<List<String>> result = new ArrayList<>();
            for(PrefixTree subtree : children.values()) {
                if(subtree instanceof PrefixParentNode) {
                    List<String> intermediate = new ArrayList<>();
                    getUntilTerminalOrMultipleChildren(intermediate, (PrefixParentNode)subtree);
                    result.add(intermediate);
                } else {
                    List<String> root = Collections.singletonList("root");
                    if(!result.contains(root)) {
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
                if(!(node instanceof PrefixParentNode)) {
                    break;
                }
                resultSoFar.add(node.getName());
                children = ((PrefixParentNode)node).children.values().stream().collect(Collectors.toList());
                node = children.get(0);
            } while (children.size() == 1 && children.get(0) instanceof PrefixParentNode);
        }
    }
}

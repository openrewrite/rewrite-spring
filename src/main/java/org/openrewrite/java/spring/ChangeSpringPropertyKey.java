/*
 * Copyright 2024 the original author or authors.
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

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.ChangePropertyKey;
import org.openrewrite.yaml.UnfoldProperties;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.singletonList;
import static java.util.regex.Pattern.quote;

@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
@Value
public class ChangeSpringPropertyKey extends ScanningRecipe<SpringConfigFiles> {

    private static final AnnotationMatcher VALUE_MATCHER =
            new AnnotationMatcher("@org.springframework.beans.factory.annotation.Value");
    private static final AnnotationMatcher CONDITIONAL_ON_PROPERTY_MATCHER =
            new AnnotationMatcher("@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty");
    private static final AnnotationMatcher SPRING_BOOT_TEST_MATCHER =
            new AnnotationMatcher("@org.springframework.boot..*Test");

    String displayName = "Change the key of a Spring application property";

    String description = "Change Spring application property keys existing in either Properties or YAML files, and in `@Value`, `@ConditionalOnProperty` or `@SpringBootTest` annotations.";

    @Option(displayName = "Old property key",
            description = "The property key to rename.",
            example = "management.metrics.binders.*.enabled")
    String oldPropertyKey;

    @Option(displayName = "New property key",
            description = "The new name for the property key.",
            example = "management.metrics.enable.process.files")
    String newPropertyKey;

    @Option(displayName = "Except",
            description = "Regex. If any of these property keys exist as direct children of `oldPropertyKey`, then they will not be moved to `newPropertyKey`.",
            required = false,
            example = "jvm")
    @Nullable
    List<String> except;

    @NonFinal
    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    transient @Nullable Compiled compiled;

    @Override
    public SpringConfigFiles getInitialValue(ExecutionContext ctx) {
        return new SpringConfigFiles();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(SpringConfigFiles acc) {
        return acc.getScanner();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(SpringConfigFiles acc) {
        Compiled compiled = compiled();
        return Preconditions.check(Preconditions.or(
                new IsPossibleSpringConfigFile(acc),
                new UsesType<>("org.springframework.beans.factory.annotation.Value", false),
                new UsesType<>("org.springframework.boot.autoconfigure.condition.ConditionalOnProperty", false),
                new UsesType<>("org.springframework.boot..*Test", false)
        ), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Yaml.Documents) {
                    ChangePropertyKey changeKey = compiled.exceptPattern == null ? compiled.yamlChangePropertyKey :
                            new ChangePropertyKey(oldPropertyKey, newPropertyKey, true, exceptedYamlSubKeys((Yaml.Documents) tree, compiled.exceptPattern), null);
                    Tree newTree = changeKey.getVisitor().visit(tree, ctx);
                    if (newTree != tree && isWrittenAsNestedMappings((Yaml.Documents) tree)) {
                        newTree = compiled.unfoldNewPropertyKey.getVisitor().visit(newTree, ctx);
                    }
                    tree = newTree;
                } else if (tree instanceof Properties.File) {
                    if (FindProperties.find((Properties.File) tree, newPropertyKey, true).isEmpty()) {
                        Tree newTree = compiled.propertiesChangePropertyKey.getVisitor().visit(tree, ctx);
                        // for compatibility with yaml syntax, a spring property key will never have both a (scalar) value and also subproperties
                        if (newTree == tree) {
                            newTree = compiled.subpropertiesChangePropertyKey.getVisitor().visit(tree, ctx);
                        }
                        tree = newTree;
                    }
                } else if (tree instanceof JavaSourceFile) {
                    tree = new JavaPropertyKeyVisitor(compiled.valueReferencePattern, compiled.prefixPattern, compiled.oldKeyPattern).visit(tree, ctx);
                }
                return tree;
            }
        });
    }

    private Compiled compiled() {
        Compiled c = compiled;
        if (c == null || !c.isFor(oldPropertyKey, newPropertyKey, except)) {
            c = new Compiled(oldPropertyKey, newPropertyKey, except == null ? null : new ArrayList<>(except));
            compiled = c;
        }
        return c;
    }

    /// The sub-recipes and patterns derived from the options, built once per recipe instance rather than for every
    /// source file, as `ScanningRecipe#getVisitor()` asks for a new visitor per source file.
    private static final class Compiled {
        final String oldPropertyKey;
        final String newPropertyKey;
        final @Nullable List<String> except;
        final ChangePropertyKey yamlChangePropertyKey;
        final org.openrewrite.properties.ChangePropertyKey propertiesChangePropertyKey;
        final org.openrewrite.properties.ChangePropertyKey subpropertiesChangePropertyKey;
        final UnfoldProperties unfoldNewPropertyKey;
        final Pattern valueReferencePattern;
        final Pattern prefixPattern;
        final Pattern oldKeyPattern;
        final @Nullable Pattern exceptPattern;

        Compiled(String oldPropertyKey, String newPropertyKey, @Nullable List<String> except) {
            this.oldPropertyKey = oldPropertyKey;
            this.newPropertyKey = newPropertyKey;
            this.except = except;
            String exceptRegex = exceptRegex(except);
            String quotedOldKey = quote(oldPropertyKey);
            yamlChangePropertyKey = new ChangePropertyKey(oldPropertyKey, newPropertyKey, true, except, null);
            propertiesChangePropertyKey = new org.openrewrite.properties.ChangePropertyKey(oldPropertyKey, newPropertyKey, true, false);
            subpropertiesChangePropertyKey = new org.openrewrite.properties.ChangePropertyKey(quotedOldKey + exceptRegex + "([.\\[].+)", newPropertyKey + "$1", true, true);
            unfoldNewPropertyKey = new UnfoldProperties(null, singletonList("$." + newPropertyKey));
            valueReferencePattern = Pattern.compile("\\$\\{(" + quotedOldKey + exceptRegex + "(?:\\.[^.}:]+)*)((:(?:\\\\.|[^}])*)?\\})");
            prefixPattern = Pattern.compile("^" + quotedOldKey + exceptRegex + "(?![\\w-])");
            oldKeyPattern = Pattern.compile(quotedOldKey);
            exceptPattern = except == null || except.isEmpty() ? null : Pattern.compile("(?:" + String.join("|", except) + ")\\b");
        }

        /// Remembers the options it was built from, as `Recipe#withOptions` shallow clones a recipe, sharing this cache,
        /// and then overwrites the options in place. `except` is copied so later changes to the caller's list are noticed.
        boolean isFor(String oldPropertyKey, String newPropertyKey, @Nullable List<String> except) {
            return this.oldPropertyKey.equals(oldPropertyKey) && this.newPropertyKey.equals(newPropertyKey) && Objects.equals(this.except, except);
        }
    }

    /**
     * Spring configuration accepts both `a: {b: {c: value}}` and `a.b.c: value`; only unfold the renamed key back into
     * nested mappings when the original file spelled the old key out as one mapping entry per segment, such that we do
     * not convert a deliberately flattened file to nested mappings.
     */
    private boolean isWrittenAsNestedMappings(Yaml.Documents documents) {
        return new YamlIsoVisitor<AtomicBoolean>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, AtomicBoolean found) {
                if (!found.get() && !entry.getKey().getValue().contains(".") &&
                        NameCaseConvention.matchesGlobRelaxedBinding(propertyPath(entry), oldPropertyKey)) {
                    found.set(true);
                }
                return super.visitMappingEntry(entry, found);
            }

            private String propertyPath(Yaml.Mapping.Entry entry) {
                StringBuilder path = new StringBuilder(entry.getKey().getValue());
                for (Cursor c = getCursor().getParent(); c != null; c = c.getParent()) {
                    if (c.getValue() instanceof Yaml.Mapping.Entry) {
                        path.insert(0, ((Yaml.Mapping.Entry) c.getValue()).getKey().getValue() + '.');
                    }
                }
                return path.toString();
            }
        }.reduce(documents, new AtomicBoolean()).get();
    }

    /**
     * `except` is a list of regexes, but the YAML recipe compares its exclusions literally, so resolve the regexes
     * against the child keys of `oldPropertyKey` actually present in the file.
     */
    private List<String> exceptedYamlSubKeys(Yaml.Documents documents, Pattern exceptPattern) {
        int subKeyIndex = oldPropertyKey.split("\\.").length;
        Set<String> subKeys = new YamlIsoVisitor<Set<String>>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, Set<String> acc) {
                String path = getCursor().getPathAsStream()
                        .filter(Yaml.Mapping.Entry.class::isInstance)
                        .map(e -> ((Yaml.Mapping.Entry) e).getKey().getValue())
                        .reduce((child, parent) -> parent + '.' + child)
                        .orElse("");
                String[] segments = path.split("\\.");
                if (segments.length > subKeyIndex &&
                        NameCaseConvention.matchesGlobRelaxedBinding(path, oldPropertyKey + ".*") &&
                        exceptPattern.matcher(segments[subKeyIndex]).lookingAt()) {
                    acc.add(segments[subKeyIndex]);
                }
                return super.visitMappingEntry(entry, acc);
            }
        }.reduce(documents, new LinkedHashSet<>());
        return new ArrayList<>(subKeys);
    }

    private static String exceptRegex(@Nullable List<String> except) {
        return except == null || except.isEmpty() ?
                "" :
                "(?!\\.(?:" + String.join("|", except) + ")\\b)";
    }

    private class JavaPropertyKeyVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final Pattern valueReferencePattern;
        private final Pattern prefixPattern;
        private final Pattern oldKeyPattern;

        JavaPropertyKeyVisitor(Pattern valueReferencePattern, Pattern prefixPattern, Pattern oldKeyPattern) {
            this.valueReferencePattern = valueReferencePattern;
            this.prefixPattern = prefixPattern;
            this.oldKeyPattern = oldKeyPattern;
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = annotation;

            if (VALUE_MATCHER.matches(annotation)) {
                if (a.getArguments() != null) {
                    a = a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                        if (arg instanceof J.Literal) {
                            J.Literal literal = (J.Literal) arg;
                            if (literal.getValue() instanceof String && literal.getValueSource() != null) {
                                String value = (String) literal.getValue();
                                if (value.contains(oldPropertyKey)) {
                                    if (newPropertyKey.contains(oldPropertyKey) && value.contains(newPropertyKey)) {
                                        return arg;
                                    }
                                    String newValue = renamePropertyPlaceholders(value);
                                    if (newValue != null && !value.equals(newValue)) {
                                        if (except != null) {
                                            for (String e : except) {
                                                if (newValue.contains("${" + newPropertyKey + '.' + e)) {
                                                    return arg;
                                                }
                                            }
                                        }
                                        String newValueSource = renamePropertyPlaceholders(literal.getValueSource());
                                        if (newValueSource != null) {
                                            return literal.withValue(newValue).withValueSource(newValueSource);
                                        }
                                    }
                                }
                            }
                        }
                        return arg;
                    }));
                }
            } else if (CONDITIONAL_ON_PROPERTY_MATCHER.matches(annotation)) {
                if (a.getArguments() != null) {
                    a = a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                        if (arg instanceof J.Assignment && "name".equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName())) {
                            if (((J.Assignment) arg).getAssignment() instanceof J.Literal) {
                                J.Assignment assignment = (J.Assignment) arg;
                                J.Literal literal = (J.Literal) assignment.getAssignment();
                                J.Literal newLiteral = changePropertyInLiteral(literal);
                                if (newLiteral != literal) {
                                    return assignment.withAssignment(newLiteral);
                                }
                            }
                            if (((J.Assignment) arg).getAssignment() instanceof K.ListLiteral) {
                                J.Assignment assignment = (J.Assignment) arg;
                                K.ListLiteral listLiteral = (K.ListLiteral) assignment.getAssignment();
                                return assignment.withAssignment(listLiteral.withElements(ListUtils.map(listLiteral.getElements(), element -> {
                                    if (element instanceof J.Literal) {
                                        J.Literal literal = (J.Literal) element;
                                        J.Literal newLiteral = changePropertyInLiteral(literal);
                                        if (newLiteral != literal) {
                                            return newLiteral;
                                        }
                                    }
                                    return element;
                                })));
                            }
                        }
                        return arg;
                    }));
                }
            } else if (SPRING_BOOT_TEST_MATCHER.matches(annotation)) {
                a = a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                    if (arg instanceof J.NewArray) {
                        J.NewArray array = (J.NewArray) arg;
                        return array.withInitializer(ListUtils.map(array.getInitializer(),
                                property -> property instanceof J.Literal ? changePropertyInLiteral((J.Literal) property) : property));
                    }
                    if (arg instanceof J.Literal) {
                        return changePropertyInLiteral((J.Literal) arg);
                    }
                    if (arg instanceof J.Assignment &&
                            "properties".equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName())) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getAssignment() instanceof J.Literal) {
                            J.Literal literal = (J.Literal) assignment.getAssignment();
                            J.Literal newLiteral = changePropertyInLiteral(literal);
                            if (newLiteral != literal) {
                                return assignment.withAssignment(newLiteral);
                            }
                        } else if (assignment.getAssignment() instanceof J.NewArray) {
                            J.NewArray array = (J.NewArray) assignment.getAssignment();
                            return assignment.withAssignment(array.withInitializer(ListUtils.map(array.getInitializer(),
                                    property -> property instanceof J.Literal ? changePropertyInLiteral((J.Literal) property) : property)));
                        }

                    }
                    if (arg instanceof J.Lambda) {
                        J.Lambda lambda = (J.Lambda) arg;
                        return lambda.withBody(lambda.getBody() instanceof J.Block ?
                                ((J.Block) lambda.getBody()).withStatements(ListUtils.map(((J.Block) lambda.getBody()).getStatements(), statement -> {
                                    if (statement instanceof K.ExpressionStatement &&
                                        ((K.ExpressionStatement) statement).getExpression() instanceof J.Literal) {
                                        J.Literal literal = (J.Literal) ((K.ExpressionStatement) statement).getExpression();
                                        J.Literal newLiteral = changePropertyInLiteral(literal);
                                        if (newLiteral != literal) {
                                            return ((K.ExpressionStatement) statement).withExpression(newLiteral);
                                        }
                                    }
                                    return statement;
                                })) :
                                lambda.getBody());
                    }
                    return arg;
                }));
            }

            return a;
        }

        private J.Literal changePropertyInLiteral(J.Literal literal) {
            if (literal.getValue() == null || literal.getValueSource() == null) {
                return literal;
            }
            String value = literal.getValue().toString();
            if (!value.contains(oldPropertyKey)) {
                return literal;
            }
            if (newPropertyKey.contains(oldPropertyKey) && value.contains(newPropertyKey)) {
                return literal;
            }
            Matcher matcher = prefixPattern.matcher(value);
            if (matcher.find()) {
                return literal
                        .withValue(oldKeyPattern.matcher(value).replaceFirst(newPropertyKey))
                        .withValueSource(oldKeyPattern.matcher(literal.getValueSource()).replaceFirst(newPropertyKey));
            }
            return literal;
        }

        private @Nullable String renamePropertyPlaceholders(String input) {
            Matcher matcher = valueReferencePattern.matcher(input);
            if (!matcher.find()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            int idx = 0;
            do {
                sb.append(input, idx, matcher.start());
                idx = matcher.end();
                sb.append("${")
                        .append(oldKeyPattern.matcher(matcher.group(1)).replaceFirst(newPropertyKey))
                        .append(matcher.group(2));
            } while (matcher.find());
            sb.append(input, idx, input.length());
            return sb.toString();
        }
    }
}

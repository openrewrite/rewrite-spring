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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.ChangePropertyKey;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.regex.Pattern.quote;

/**
 * This composite recipe will change a spring application property key across YAML and properties files.
 * It also changes property keys in @Value annotations.
 */
@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeSpringPropertyKey extends Recipe {

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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        ChangePropertyKey yamlChangePropertyKey =
                new ChangePropertyKey(oldPropertyKey, newPropertyKey, true, except, null);
        org.openrewrite.properties.ChangePropertyKey propertiesChangePropertyKey =
                new org.openrewrite.properties.ChangePropertyKey(oldPropertyKey, newPropertyKey, true, false);
        org.openrewrite.properties.ChangePropertyKey subpropertiesChangePropertyKey =
                new org.openrewrite.properties.ChangePropertyKey(quote(oldPropertyKey) + exceptRegex() + "(.+)", newPropertyKey + "$1", true, true);
        NormalizeInsertedYamlParentVisitor normalizeInsertedYamlParentVisitor = new NormalizeInsertedYamlParentVisitor();
        CoalesceYamlMappingsVisitor coalesceYamlMappingsVisitor = new CoalesceYamlMappingsVisitor();

        return Preconditions.check(Preconditions.or(
                new IsPossibleSpringConfigFile(),
                new UsesType<>("org.springframework.beans.factory.annotation.Value", false),
                new UsesType<>("org.springframework.boot.autoconfigure.condition.ConditionalOnProperty", false),
                new UsesType<>("org.springframework.boot..*Test", false)
        ), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Yaml.Documents) {
                    tree = yamlChangePropertyKey.getVisitor().visit(tree, ctx);
                    if (tree instanceof Yaml.Documents) {
                        tree = normalizeInsertedYamlParentVisitor.visit(tree, ctx);
                        tree = coalesceYamlMappingsVisitor.visit(tree, ctx);
                    }
                } else if (tree instanceof Properties.File) {
                    if (FindProperties.find((Properties.File) tree, newPropertyKey, true).isEmpty()) {
                        Tree newTree = propertiesChangePropertyKey.getVisitor().visit(tree, ctx);
                        // for compatibility with yaml syntax, a spring property key will never have both a (scalar) value and also subproperties
                        if (newTree == tree) {
                            newTree = subpropertiesChangePropertyKey.getVisitor().visit(tree, ctx);
                        }
                        tree = newTree;
                    }
                } else if (tree instanceof JavaSourceFile) {
                    tree = new JavaPropertyKeyVisitor().visit(tree, ctx);
                }
                return tree;
            }
        });
    }

    private String exceptRegex() {
        return except == null || except.isEmpty() ?
                "" :
                "(?!\\.(?:" + String.join("|", except) + ")\\b)";
    }

    private class JavaPropertyKeyVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = annotation;

            if (VALUE_MATCHER.matches(annotation)) {
                if (a.getArguments() != null) {
                    a = a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                        if (arg instanceof J.Literal) {
                            J.Literal literal = (J.Literal) arg;
                            if (literal.getValue() instanceof String) {
                                String value = (String) literal.getValue();
                                if (value.contains(oldPropertyKey)) {
                                    if (newPropertyKey.contains(oldPropertyKey) && value.contains(newPropertyKey)) {
                                        return arg;
                                    }
                                    Pattern pattern = Pattern.compile("\\$\\{(" + quote(oldPropertyKey) + exceptRegex() + "(?:\\.[^.}:]+)*)(((?:\\\\.|[^}])*)\\})");
                                    Matcher matcher = pattern.matcher(value);
                                    int idx = 0;
                                    if (matcher.find()) {
                                        StringBuilder sb = new StringBuilder();
                                        do {
                                            sb.append(value, idx, matcher.start());
                                            idx = matcher.end();
                                            sb.append("${")
                                                    .append(matcher.group(1).replaceFirst(quote(oldPropertyKey), newPropertyKey))
                                                    .append(matcher.group(2));
                                        } while (matcher.find());
                                        sb.append(value, idx, value.length());

                                        String newValue = sb.toString();

                                        if (!value.equals(newValue)) {
                                            if (except != null) {
                                                for (String e : except) {
                                                    if (newValue.contains("${" + newPropertyKey + '.' + e)) {
                                                        return arg;
                                                    }
                                                }
                                            }
                                            int leadingBackslashes = 0;
                                            for (int i = 0; i < newValue.length(); i++) {
                                                if (newValue.charAt(i) == '\\') {
                                                    leadingBackslashes++;
                                                } else {
                                                    break;
                                                }
                                            }

                                            return literal.withValue(newValue)
                                                    .withValueSource("\"" + StringUtils.repeat("\\", leadingBackslashes) + newValue.substring(leadingBackslashes).replace("\\", "\\\\") + "\"");
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
            if (newPropertyKey.contains(oldPropertyKey) && value.contains(newPropertyKey)) {
                return literal;
            }
            Pattern pattern = Pattern.compile("^" + quote(oldPropertyKey) + exceptRegex());
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                return literal
                        .withValue(value.replaceFirst(quote(oldPropertyKey), newPropertyKey))
                        .withValueSource(literal.getValueSource().replaceFirst(quote(oldPropertyKey), newPropertyKey));
            }
            return literal;
        }
    }

    private class NormalizeInsertedYamlParentVisitor extends YamlIsoVisitor<ExecutionContext> {
        @Override
        public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
            Yaml.Mapping m = super.visitMapping(mapping, ctx);
            return m.withEntries(ListUtils.map(m.getEntries(), entry -> relocateDottedTargetEntry(m, entry, ctx)));
        }

        private Yaml.Mapping.Entry relocateDottedTargetEntry(Yaml.Mapping mapping, Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            String key = entry.getKey().getValue();
            if (!matchesInsertedTargetKey(key)) {
                return entry;
            }

            List<String> segments = Arrays.asList(key.split("\\."));
            int deepestExistingPrefix = deepestExistingPrefix(mapping, entry, segments);
            if (deepestExistingPrefix <= 0 || deepestExistingPrefix >= segments.size()) {
                return entry;
            }

            return autoFormat(buildNestedEntry(entry, segments, deepestExistingPrefix), ctx, getCursor());
        }

        private boolean matchesInsertedTargetKey(String key) {
            return key.equals(newPropertyKey) || key.startsWith(newPropertyKey + ".");
        }

        private int deepestExistingPrefix(Yaml.Mapping mapping, Yaml.Mapping.Entry candidate, List<String> segments) {
            Yaml.Mapping currentMapping = mapping;
            int matched = 0;
            for (int i = 0; i < segments.size() - 1; i++) {
                Yaml.Mapping.Entry existingEntry = findEntry(currentMapping, segments.get(i), i == 0 ? candidate : null);
                if (existingEntry == null || !(existingEntry.getValue() instanceof Yaml.Mapping)) {
                    break;
                }
                matched = i + 1;
                currentMapping = (Yaml.Mapping) existingEntry.getValue();
            }
            return matched;
        }

        private Yaml.Mapping.Entry findEntry(Yaml.Mapping mapping, String key, Yaml.Mapping.Entry exclude) {
            for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
                if (entry != exclude && entry.getKey().getValue().equals(key)) {
                    return entry;
                }
            }
            return null;
        }

        private Yaml.Mapping.Entry buildNestedEntry(Yaml.Mapping.Entry originalEntry, List<String> segments, int matchedSegments) {
            Yaml.Mapping.Entry nestedEntry = new Yaml.Mapping.Entry(
                    Tree.randomId(),
                    "",
                    Markers.EMPTY,
                    new Yaml.Scalar(Tree.randomId(), "", Markers.EMPTY, Yaml.Scalar.Style.PLAIN, null, null,
                            String.join(".", segments.subList(matchedSegments, segments.size()))),
                    originalEntry.getBeforeMappingValueIndicator(),
                    originalEntry.getValue()
            );

            for (int i = matchedSegments - 1; i >= 0; i--) {
                nestedEntry = new Yaml.Mapping.Entry(
                        Tree.randomId(),
                        i == 0 ? originalEntry.getPrefix() : "",
                        Markers.EMPTY,
                        new Yaml.Scalar(Tree.randomId(), "", Markers.EMPTY, Yaml.Scalar.Style.PLAIN, null, null, segments.get(i)),
                        "",
                        new Yaml.Mapping(
                                Tree.randomId(),
                                Markers.EMPTY,
                                null,
                                Collections.singletonList(nestedEntry),
                                null,
                                null,
                                null
                        )
                );
            }

            return nestedEntry;
        }
    }

    private class CoalesceYamlMappingsVisitor extends YamlIsoVisitor<ExecutionContext> {
        @Override
        public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
            Yaml.Mapping m = super.visitMapping(mapping, ctx);
            Map<String, List<Yaml.Mapping>> mappingsByKey = new HashMap<>();
            for (Yaml.Mapping.Entry entry : m.getEntries()) {
                if (entry.getValue() instanceof Yaml.Mapping) {
                    mappingsByKey.computeIfAbsent(entry.getKey().getValue(), key -> new ArrayList<>()).add((Yaml.Mapping) entry.getValue());
                }
            }

            for (Map.Entry<String, List<Yaml.Mapping>> keyMappings : mappingsByKey.entrySet()) {
                if (keyMappings.getValue().size() > 1) {
                    Yaml.Mapping mergedMapping = new Yaml.Mapping(
                            Tree.randomId(),
                            Markers.EMPTY,
                            null,
                            keyMappings.getValue().stream()
                                    .flatMap(duplicateMapping -> duplicateMapping.getEntries().stream())
                                    .collect(Collectors.toList()),
                            null,
                            null,
                            null
                    );
                    mergedMapping = coalesceDuplicateMappings(mergedMapping);

                    Yaml.Mapping.Entry mergedEntry = autoFormat(
                            new Yaml.Mapping.Entry(
                                    Tree.randomId(),
                                    "",
                                    Markers.EMPTY,
                                    new Yaml.Scalar(Tree.randomId(), "", Markers.EMPTY, Yaml.Scalar.Style.PLAIN, null, null, keyMappings.getKey()),
                                    "",
                                    mergedMapping
                            ),
                            ctx,
                            getCursor()
                    );

                    AtomicInteger insertIndex = new AtomicInteger(-1);
                    m = m.withEntries(ListUtils.map(m.getEntries(), (i, entry) -> {
                        if (entry.getKey().getValue().equals(keyMappings.getKey()) && entry.getValue() instanceof Yaml.Mapping) {
                            if (insertIndex.get() < 0) {
                                insertIndex.set(i);
                            }
                            return null;
                        }
                        return entry;
                    }));
                    m = m.withEntries(ListUtils.insertAll(m.getEntries(), insertIndex.get(), Collections.singletonList(mergedEntry)));
                }
            }
            return m;
        }

        private Yaml.Mapping coalesceDuplicateMappings(Yaml.Mapping mapping) {
            Yaml.Mapping coalesced = mapping.withEntries(ListUtils.map(mapping.getEntries(), entry -> {
                if (entry.getValue() instanceof Yaml.Mapping) {
                    return entry.withValue(coalesceDuplicateMappings((Yaml.Mapping) entry.getValue()));
                }
                return entry;
            }));

            Map<String, List<Yaml.Mapping>> mappingsByKey = new HashMap<>();
            for (Yaml.Mapping.Entry entry : coalesced.getEntries()) {
                if (entry.getValue() instanceof Yaml.Mapping) {
                    mappingsByKey.computeIfAbsent(entry.getKey().getValue(), key -> new ArrayList<>()).add((Yaml.Mapping) entry.getValue());
                }
            }

            for (Map.Entry<String, List<Yaml.Mapping>> keyMappings : mappingsByKey.entrySet()) {
                if (keyMappings.getValue().size() > 1) {
                    Yaml.Mapping mergedMapping = coalesceDuplicateMappings(new Yaml.Mapping(
                            Tree.randomId(),
                            Markers.EMPTY,
                            null,
                            keyMappings.getValue().stream()
                                    .flatMap(duplicateMapping -> duplicateMapping.getEntries().stream())
                                    .collect(Collectors.toList()),
                            null,
                            null,
                            null
                    ));

                    AtomicInteger insertIndex = new AtomicInteger(-1);
                    coalesced = coalesced.withEntries(ListUtils.map(coalesced.getEntries(), (i, entry) -> {
                        if (entry.getKey().getValue().equals(keyMappings.getKey()) && entry.getValue() instanceof Yaml.Mapping) {
                            if (insertIndex.get() < 0) {
                                insertIndex.set(i);
                            }
                            return null;
                        }
                        return entry;
                    }));
                    coalesced = coalesced.withEntries(ListUtils.insertAll(
                            coalesced.getEntries(),
                            insertIndex.get(),
                            Collections.singletonList(new Yaml.Mapping.Entry(
                                    Tree.randomId(),
                                    "",
                                    Markers.EMPTY,
                                    new Yaml.Scalar(Tree.randomId(), "", Markers.EMPTY, Yaml.Scalar.Style.PLAIN, null, null, keyMappings.getKey()),
                                    "",
                                    mergedMapping
                            ))
                    ));
                }
            }

            return coalesced;
        }
    }
}

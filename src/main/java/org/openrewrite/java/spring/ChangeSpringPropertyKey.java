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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.ChangePropertyKey;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.quote;

/**
 * This composite recipe will change a spring application property key across YAML and properties files.
 * It also changes property keys in @Value annotations.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeSpringPropertyKey extends Recipe {

    @Override
    public String getDisplayName() {
        return "Change the key of a Spring application property";
    }

    @Override
    public String getDescription() {
        return "Change Spring application property keys existing in either Properties or YAML files, and in `@Value` annotations.";
    }

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

        return Preconditions.check(Preconditions.or(
                new IsPossibleSpringConfigFile(),
                new UsesType<>("org.springframework.beans.factory.annotation.Value", false),
                new UsesType<>("org.springframework.boot.autoconfigure.condition.ConditionalOnProperty", false)
        ), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Yaml.Documents) {
                    tree = yamlChangePropertyKey.getVisitor().visit(tree, ctx);
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
        private final AnnotationMatcher VALUE_MATCHER =
                new AnnotationMatcher("@org.springframework.beans.factory.annotation.Value");
        private final AnnotationMatcher CONDITIONAL_ON_PROPERTY_MATCHER =
                new AnnotationMatcher("@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty");

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
                                            arg = literal.withValue(newValue)
                                                    .withValueSource("\"" + newValue.replace("\\", "\\\\") + "\"");
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
                        if (arg instanceof J.Assignment &&
                            ((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName().equals("name") &&
                            ((J.Assignment) arg).getAssignment() instanceof J.Literal) {
                            J.Assignment assignment = (J.Assignment) arg;
                            J.Literal literal = (J.Literal) assignment.getAssignment();
                            String value = literal.getValue().toString();

                            Pattern pattern = Pattern.compile("^" + quote(oldPropertyKey) + exceptRegex());
                            Matcher matcher = pattern.matcher(value);
                            if (matcher.find()) {
                                arg = assignment.withAssignment(
                                        literal.withValueSource(
                                                        literal.getValueSource().replaceFirst(quote(oldPropertyKey), newPropertyKey))
                                                .withValue(value.replaceFirst(quote(oldPropertyKey), newPropertyKey))
                                );
                            }
                        }
                        return arg;
                    }));
                }
            }

            return a;
        }
    }
}

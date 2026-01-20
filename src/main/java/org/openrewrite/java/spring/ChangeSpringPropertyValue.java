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
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeSpringPropertyValue extends Recipe {

    String displayName = "Change the value of a spring application property";

    String description = "Change Spring application property values existing in either Properties or YAML files, " +
                         "and in `@Value`, `@ConditionalOnProperty`, `@SpringBootTest`, or `@TestPropertySource` annotations.";

    @Option(displayName = "Property key",
            description = "The name of the property key whose value is to be changed.",
            example = "management.metrics.binders.files.enabled")
    String propertyKey;

    @Option(displayName = "New value",
            description = "The new value to be used for key specified by `propertyKey`.",
            example = "management.metrics.enable.process.files")
    String newValue;

    @Option(displayName = "Old value",
            required = false,
            description = "Only change the property value if it matches the configured `oldValue`.",
            example = "false")
    @Nullable
    String oldValue;

    @Option(displayName = "Regex",
            description = "Default false. If enabled, `oldValue` will be interpreted as a Regular Expression, and capture group contents will be available in `newValue`",
            required = false)
    @Nullable
    Boolean regex;

    @Option(displayName = "Use relaxed binding",
            description = "Whether to match the `propertyKey` using [relaxed binding](https://docs.spring.io/spring-boot/docs/2.5.6/reference/html/features.html#features.external-config.typesafe-configuration-properties.relaxed-binding) " +
                          "rules. Default is `true`. Set to `false` to use exact matching.",
            required = false)
    @Nullable
    Boolean relaxedBinding;

    @Override
    public Validated<Object> validate() {
        return super.validate().and(
                Validated.test("oldValue", "is required if `regex` is enabled", oldValue,
                        value -> !(Boolean.TRUE.equals(regex) && StringUtils.isNullOrEmpty(value))));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        Recipe changeProperties = new org.openrewrite.properties.ChangePropertyValue(propertyKey, newValue, oldValue, regex, relaxedBinding);
        String yamlValue = quoteValue(newValue) ? "\"" + newValue + "\"" : newValue;
        Recipe changeYaml = new org.openrewrite.yaml.ChangePropertyValue(propertyKey, yamlValue, oldValue, regex, relaxedBinding, null);
        TreeVisitor<?, ExecutionContext> javaVisitor = Preconditions.check(Preconditions.or(
                new UsesType<>("org.springframework.beans.factory.annotation.Value", false),
                new UsesType<>("org.springframework.boot.autoconfigure.condition.ConditionalOnProperty", false),
                new UsesType<>("org.springframework.boot..*Test", false),
                new UsesType<>("org.springframework.test.context.TestPropertySource", false)
        ), new JavaPropertyValueVisitor());
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Properties.File) {
                    tree = changeProperties.getVisitor().visit(tree, ctx);
                } else if (tree instanceof Yaml.Documents) {
                    tree = changeYaml.getVisitor().visit(tree, ctx);
                } else if (tree instanceof JavaSourceFile) {
                    tree = javaVisitor.visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    private static final Pattern scalarNeedsAQuote = Pattern.compile("[^a-zA-Z\\d\\s]*");
    private boolean quoteValue(String value) {
        return scalarNeedsAQuote.matcher(value).matches();
    }

    private class JavaPropertyValueVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final AnnotationMatcher VALUE_MATCHER =
                new AnnotationMatcher("@org.springframework.beans.factory.annotation.Value");
        private final AnnotationMatcher CONDITIONAL_ON_PROPERTY_MATCHER =
                new AnnotationMatcher("@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty");
        private final AnnotationMatcher SPRING_BOOT_TEST_MATCHER =
                new AnnotationMatcher("@org.springframework.boot..*Test");
        private final AnnotationMatcher TEST_PROPERTY_SOURCE_MATCHER =
                new AnnotationMatcher("@org.springframework.test.context.TestPropertySource");

        // Pattern to match ${key:defaultValue} in @Value annotations
        private final Pattern valueAnnotationPattern = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
        // Pattern to match key=value in test annotations
        private final Pattern keyValuePattern = Pattern.compile("^([^=]+)=(.*)$");

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);

            if (VALUE_MATCHER.matches(a)) {
                a = handleValueAnnotation(a);
            } else if (CONDITIONAL_ON_PROPERTY_MATCHER.matches(a)) {
                a = handleConditionalOnPropertyAnnotation(a);
            } else if (SPRING_BOOT_TEST_MATCHER.matches(a) || TEST_PROPERTY_SOURCE_MATCHER.matches(a)) {
                a = handleTestPropertiesAnnotation(a);
            }

            return a;
        }

        private J.Annotation handleValueAnnotation(J.Annotation annotation) {
            return annotation.withArguments(ListUtils.map(annotation.getArguments(), arg -> {
                if (arg instanceof J.Literal) {
                    return changeValueInValueAnnotation((J.Literal) arg);
                }
                return arg;
            }));
        }

        private J.Literal changeValueInValueAnnotation(J.Literal literal) {
            if (!(literal.getValue() instanceof String)) {
                return literal;
            }
            String value = (String) literal.getValue();
            String valueSource = literal.getValueSource();
            Matcher matcher = valueAnnotationPattern.matcher(value);

            boolean changed = false;
            StringBuilder newValue = new StringBuilder();
            int lastEnd = 0;

            while (matcher.find()) {
                String key = matcher.group(1);
                String defaultValue = matcher.group(2);

                if (matchesPropertyKey(key) && defaultValue != null && matchesOldValue(defaultValue)) {
                    String computedNewValue = computeNewValue(defaultValue);
                    if (!computedNewValue.equals(defaultValue)) {
                        newValue.append(value, lastEnd, matcher.start());
                        newValue.append("${").append(key).append(":").append(computedNewValue).append("}");
                        lastEnd = matcher.end();

                        // Also update valueSource by replacing the old default with new default
                        // This preserves all other escaping (e.g., \$ in Kotlin)
                        if (valueSource != null) {
                            valueSource = valueSource.replace(defaultValue, computedNewValue);
                        }

                        changed = true;
                    }
                }
            }

            if (changed) {
                newValue.append(value, lastEnd, value.length());
                String newValueStr = newValue.toString();
                if (valueSource == null) {
                    valueSource = "\"" + newValueStr + "\"";
                }
                return literal.withValue(newValueStr).withValueSource(valueSource);
            }
            return literal;
        }

        private J.Annotation handleConditionalOnPropertyAnnotation(J.Annotation annotation) {
            if (annotation.getArguments() == null) {
                return annotation;
            }

            // First, find the property key from 'name' or 'value' attribute
            String foundKey = null;
            for (Expression arg : annotation.getArguments()) {
                if (arg instanceof J.Assignment) {
                    J.Assignment assignment = (J.Assignment) arg;
                    String attrName = ((J.Identifier) assignment.getVariable()).getSimpleName();
                    if ("name".equals(attrName) || "value".equals(attrName)) {
                        if (assignment.getAssignment() instanceof J.Literal) {
                            Object val = ((J.Literal) assignment.getAssignment()).getValue();
                            if (val instanceof String) {
                                foundKey = (String) val;
                                break;
                            }
                        }
                    }
                } else if (arg instanceof J.Literal && ((J.Literal) arg).getValue() instanceof String) {
                    // First unnamed argument is the property name
                    foundKey = (String) ((J.Literal) arg).getValue();
                    break;
                }
            }

            if (foundKey == null || !matchesPropertyKey(foundKey)) {
                return annotation;
            }

            // Now change the 'havingValue' attribute
            return annotation.withArguments(ListUtils.map(annotation.getArguments(), arg -> {
                if (arg instanceof J.Assignment) {
                    J.Assignment assignment = (J.Assignment) arg;
                    String attrName = ((J.Identifier) assignment.getVariable()).getSimpleName();
                    if ("havingValue".equals(attrName)) {
                        if (assignment.getAssignment() instanceof J.Literal) {
                            J.Literal literal = (J.Literal) assignment.getAssignment();
                            J.Literal newLiteral = changeValueInLiteral(literal);
                            if (newLiteral != literal) {
                                return assignment.withAssignment(newLiteral);
                            }
                        }
                    }
                }
                return arg;
            }));
        }

        private J.Annotation handleTestPropertiesAnnotation(J.Annotation annotation) {
            return annotation.withArguments(ListUtils.map(annotation.getArguments(), arg -> {
                if (arg instanceof J.Assignment) {
                    J.Assignment assignment = (J.Assignment) arg;
                    String attrName = ((J.Identifier) assignment.getVariable()).getSimpleName();
                    if ("properties".equals(attrName)) {
                        if (assignment.getAssignment() instanceof J.Literal) {
                            J.Literal literal = (J.Literal) assignment.getAssignment();
                            J.Literal newLiteral = changeValueInTestProperty(literal);
                            return assignment.withAssignment(newLiteral);
                        }
                        if (assignment.getAssignment() instanceof J.NewArray) {
                            J.NewArray array = (J.NewArray) assignment.getAssignment();
                            return assignment.withAssignment(array.withInitializer(ListUtils.map(array.getInitializer(),
                                    element -> element instanceof J.Literal ? changeValueInTestProperty((J.Literal) element) : element)));
                        }
                        if (assignment.getAssignment() instanceof K.ListLiteral) {
                            K.ListLiteral listLiteral = (K.ListLiteral) assignment.getAssignment();
                            return assignment.withAssignment(listLiteral.withElements(ListUtils.map(listLiteral.getElements(),
                                    element -> element instanceof J.Literal ? changeValueInTestProperty((J.Literal) element) : element)));
                        }
                    }
                }
                return arg;
            }));
        }

        private J.Literal changeValueInTestProperty(J.Literal literal) {
            String value = (String) literal.getValue();
            Matcher matcher = keyValuePattern.matcher(value);
            if (matcher.matches()) {
                String key = matcher.group(1);
                String propValue = matcher.group(2);
                if (matchesPropertyKey(key) && matchesOldValue(propValue)) {
                    String computedNewValue = computeNewValue(propValue);
                    if (!computedNewValue.equals(propValue)) {
                        return updateLiteral(literal, key + "=" + computedNewValue);
                    }
                }
            }
            return literal;
        }

        private J.Literal changeValueInLiteral(J.Literal literal) {
            String value = (String) literal.getValue();
            if (matchesOldValue(value)) {
                String computedNewValue = computeNewValue(value);
                if (!computedNewValue.equals(value)) {
                    return updateLiteral(literal, computedNewValue);
                }
            }
            return literal;
        }

        private boolean matchesPropertyKey(String key) {
            if (!Boolean.FALSE.equals(relaxedBinding)) {
                // Normalize dots to hyphens for relaxed binding comparison
                // (NameCaseConvention doesn't handle dots as separators)
                String normalizedKey = key.replace('.', '-');
                String normalizedPropertyKey = propertyKey.replace('.', '-');
                return NameCaseConvention.equalsRelaxedBinding(normalizedKey, normalizedPropertyKey);
            }
            return key.equals(propertyKey);
        }

        private boolean matchesOldValue(String value) {
            // Don't match if the value is already the target value (idempotency)
            if (value.equals(newValue)) {
                return false;
            }
            if (oldValue == null) {
                return true;
            }
            if (Boolean.TRUE.equals(regex)) {
                return Pattern.compile(oldValue).matcher(value).find();
            }
            return value.equals(oldValue);
        }

        private String computeNewValue(String currentValue) {
            if (Boolean.TRUE.equals(regex) && oldValue != null) {
                String computed = Pattern.compile(oldValue).matcher(currentValue).replaceFirst(newValue);
                // Return original if no change to ensure idempotency
                return computed.equals(currentValue) ? currentValue : computed;
            }
            return newValue;
        }

        private J.Literal updateLiteral(J.Literal literal, String newValueStr) {
            String valueSource = literal.getValueSource();
            if (valueSource == null) {
                return literal.withValue(newValueStr).withValueSource("\"" + newValueStr + "\"");
            }
            // Handle escaping for the valueSource
            char quote = valueSource.charAt(0);
            String escapedValue = newValueStr.replace("\\", "\\\\");
            if (quote == '"') {
                escapedValue = escapedValue.replace("\"", "\\\"");
            }
            return literal.withValue(newValueStr).withValueSource(quote + escapedValue + quote);
        }
    }
}

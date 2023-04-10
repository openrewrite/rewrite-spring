/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.properties.ChangePropertyValue;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Validated;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Value
public class ChangeSpringPropertyValue extends Recipe {

    @Override
    public String getDisplayName() {
        return "Change the value of a spring application property";
    }

    @Override
    public String getDescription() {
        return "Change spring application property values existing in either Properties or Yaml files.";
    }

    @Option(displayName = "Property key",
            description = "The name of the property key whose value is to be changed.",
            example = "management.metrics.binders.files.enabled")
    String propertyKey;

    @Option(displayName = "New value",
            description = "The new value to be used for key specified by `propertyKey`.")
    String newValue;

    @Option(displayName = "Old value",
            required = false,
            description = "Only change the property value if it matches the configured `oldValue`.")
    @Nullable
    String oldValue;

    @Option(displayName = "Regex",
            description = "Default false. If enabled, `oldValue` will be interepreted as a Regular Expression, and capture group contents will be available in `newValue`",
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
    public Validated validate() {
        return super.validate().and(
                Validated.test("oldValue", "is required if `regex` is enabled", oldValue,
                        value -> !(Boolean.TRUE.equals(regex) && StringUtils.isNullOrEmpty(value))));
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        ChangePropertyValue changeProperties = new ChangePropertyValue(propertyKey, newValue, oldValue, regex,
                relaxedBinding, null);
        org.openrewrite.yaml.ChangePropertyValue changeYaml =
                new org.openrewrite.yaml.ChangePropertyValue(propertyKey, newValue, oldValue, regex,
                        relaxedBinding);

        return ListUtils.map(before, s -> {
            if (s instanceof Properties.File) {
                s = (Properties.File) changeProperties.getVisitor().visit(s, ctx);
            } else if (s instanceof Yaml.Documents) {
                s = (Yaml.Documents) changeYaml.getVisitor().visit(s, ctx);
            }
            return s;
        });
    }
}

/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.regex.Pattern;

/**
 * This composite recipe will change a spring application property key across YAML and properties files.
 * <P>
 * TODO: Add a java visitor to this recipe that will change property keys in @Value, @PropertySource and @TestPropertySource
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class ChangeSpringPropertyKey extends Recipe {

    @Override
    public String getDisplayName() {
        return "Change the key of a spring application property";
    }

    @Override
    public String getDescription() {
        return "Change spring application property keys existing in either Properties or Yaml files.";
    }

    @Option(displayName = "Old property key",
            description = "The property key to rename. Supports glob",
            example = "management.metrics.binders.*.enabled")
    String oldPropertyKey;

    @Option(displayName = "New property key",
            description = "The new name for the property key.",
            example = "management.metrics.enable.process.files")
    String newPropertyKey;

    @Option(displayName = "Except",
            description = "If any of these property keys exist as direct children of `oldPropertyKey`, then they will not be moved to `newPropertyKey`.",
            required = false)
    @Nullable
    List<String> except;

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {

        org.openrewrite.yaml.ChangePropertyKey yamlChangePropertyKey =
                new org.openrewrite.yaml.ChangePropertyKey(oldPropertyKey, newPropertyKey, true, null, except);
        org.openrewrite.properties.ChangePropertyKey propertiesChangePropertyKey =
                new org.openrewrite.properties.ChangePropertyKey(oldPropertyKey, newPropertyKey, true, null, false);
        org.openrewrite.properties.ChangePropertyKey subpropertiesChangePropertyKey =
                new org.openrewrite.properties.ChangePropertyKey(Pattern.quote(oldPropertyKey + ".") + exceptRegex() + "(.*)", newPropertyKey + ".$1", true, null, true);
        ExpandProperties expandYaml = new ExpandProperties();
        return ListUtils.map(before, s -> {
            if (s instanceof Yaml.Documents) {
                Yaml.Documents after = (Yaml.Documents) yamlChangePropertyKey.getVisitor().visit(s, ctx);
                if (after != s) {
                    s = (Yaml.Documents) expandYaml.getVisitor().visit(after, ctx);
                }
            } else if (s instanceof Properties.File) {
                s = (Properties.File) propertiesChangePropertyKey.getVisitor().visit(s, ctx);
                s = (Properties.File) subpropertiesChangePropertyKey.getVisitor().visit(s, ctx);
            }

            return s;
        });
    }

    private String exceptRegex() {
        return except == null || except.isEmpty()
                ? ""
                : "(?!(" + String.join("|", except) + "))";
    }
}

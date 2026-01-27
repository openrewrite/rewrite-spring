/*
 * Copyright 2025 the original author or authors.
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
import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

@EqualsAndHashCode(callSuper = false)
public class PropertiesToKebabCaseYaml extends Recipe {
    @Getter
    final String displayName = "Normalize Spring `application*.{yml,yaml}` properties to kebab-case";

    @Getter
    final String description = "Normalize Spring `application*.{yml,yaml}` properties to kebab-case.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles("**/application*.{yml,yaml}").getVisitor(),
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                        if (e.getKey() instanceof Yaml.Scalar) {
                            String key = e.getKey().getValue();
                            String asKebabCase = NameCaseConvention.LOWER_HYPHEN.format(key);
                            if (!key.equals(asKebabCase)) {
                                return e.withKey(((Yaml.Scalar) e.getKey()).withValue(asKebabCase));
                            }
                        }
                        return e;
                    }
                });
    }
}

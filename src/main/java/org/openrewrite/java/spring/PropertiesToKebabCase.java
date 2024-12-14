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
import org.openrewrite.*;
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.List;

public class PropertiesToKebabCase extends Recipe {

    @Override
    public String getDisplayName() {
        return "Normalize Spring properties to kebab-case";
    }

    @Override
    public String getDescription() {
        return "Normalize Spring properties to use lowercase and hyphen-separated syntax. " +
               "For example, changing `spring.main.showBanner` to `spring.main.show-banner`. " +
               "With [Spring's relaxed binding](https://docs.spring.io/spring-boot/docs/2.5.6/reference/html/features.html#features.external-config.typesafe-configuration-properties.relaxed-binding), " +
               "`kebab-case` may be used in properties files and still be converted to configuration beans. " +
               "Note, an exception to this is the case of `@Value`, which is match-sensitive. For example, `@Value(\"${anExampleValue}\")` will not match `an-example-value`. " +
               "[The Spring reference documentation recommends using `kebab-case` for properties where possible](https://docs.spring.io/spring-boot/docs/2.5.6/reference/html/features.html#features.external-config.typesafe-configuration-properties.relaxed-binding).";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(new PropertiesToKebabCaseYaml(), new PropertiesToKebabCaseProperties());
    }

    @EqualsAndHashCode(callSuper = false)
    static class PropertiesToKebabCaseYaml extends Recipe {
        @Override
        public String getDisplayName() {
            return "Normalize Spring `application*.{yml,yaml}` properties to kebab-case";
        }

        @Override
        public String getDescription() {
            return "Normalize Spring `application*.{yml,yaml}` properties to kebab-case.";
        }

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

    @EqualsAndHashCode(callSuper = false)
    static class PropertiesToKebabCaseProperties extends Recipe {
        @Override
        public String getDisplayName() {
            return "Normalize Spring `application*.properties` properties to kebab-case";
        }

        @Override
        public String getDescription() {
            return "Normalize Spring `application*.properties` properties to kebab-case.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return Preconditions.check(new FindSourceFiles("**/application*.properties"), new PropertiesIsoVisitor<ExecutionContext>() {
                @Override
                public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                    Properties.Entry e = super.visitEntry(entry, ctx);
                    String key = e.getKey();
                    String asKebabCase = NameCaseConvention.LOWER_HYPHEN.format(key);
                    if (!key.equals(asKebabCase)) {
                        return e.withKey(asKebabCase);
                    }
                    return e;
                }
            });
        }
    }
}

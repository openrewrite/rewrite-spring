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
package org.openrewrite.java.spring.boot4;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.UpgradePluginVersion;
import org.openrewrite.maven.search.FindPlugin;
import org.openrewrite.xml.AddOrUpdateChild;
import org.openrewrite.xml.FilterTagChildrenVisitor;
import org.openrewrite.xml.XmlVisitor;
import org.openrewrite.xml.search.FindTags;
import org.openrewrite.xml.tree.Xml;

import java.util.List;

import static java.util.Collections.singletonList;

public class MigrateOpenApiGeneratorToSpringBoot4 extends Recipe {

    private static final String OPENAPI_GENERATOR_GROUP_ID = "org.openapitools";
    private static final String OPENAPI_GENERATOR_ARTIFACT_ID = "openapi-generator-maven-plugin";

    @Getter
    final String displayName = "Migrate OpenAPI Generator `spring` configuration to Spring Boot 4";

    @Getter
    final String description = "Update `openapi-generator-maven-plugin` executions using the `spring` generator to " +
            "generate Spring Boot 4 and Jackson 3 sources. Replaces the deprecated `useSpringBoot3` option with " +
            "`useSpringBoot4` and enables `useJackson3`, matching the Jackson 3 baseline of Spring Boot 4. " +
            "Enabling `useSpringBoot4` also enables `useJakartaEe`, so it is left implicit. " +
            "The `useSpringBoot4`/`useJackson3` options were introduced in OpenAPI Generator 7.16.0, so the plugin " +
            "is upgraded to at least that version first.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(new UpgradePluginVersion(
                OPENAPI_GENERATOR_GROUP_ID, OPENAPI_GENERATOR_ARTIFACT_ID, "7.16.x", null, null, null));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                for (Xml.Tag plugin : FindPlugin.find(document, OPENAPI_GENERATOR_GROUP_ID, OPENAPI_GENERATOR_ARTIFACT_ID)) {
                    for (Xml.Tag configuration : FindTags.find(plugin, "//configuration")) {
                        if (isSpringGenerator(configuration) && needsMigration(configuration)) {
                            doAfterVisit(new MigrateSpringGeneratorConfiguration<>(configuration));
                        }
                    }
                }
                return super.visitDocument(document, ctx);
            }

            private boolean isSpringGenerator(Xml.Tag configuration) {
                return "spring".equals(configuration.getChildValue("generatorName").orElse(null));
            }

            private boolean needsMigration(Xml.Tag configuration) {
                return configuration.getChild("configOptions")
                        .map(configOptions -> configOptions.getChild("useSpringBoot3").isPresent() ||
                                !"true".equals(configOptions.getChildValue("useSpringBoot4").orElse(null)) ||
                                !"true".equals(configOptions.getChildValue("useJackson3").orElse(null)))
                        .orElse(true);
            }
        };
    }

    @RequiredArgsConstructor
    private static class MigrateSpringGeneratorConfiguration<P> extends XmlVisitor<P> {
        private final Xml.Tag scope;

        @Override
        public Xml visitTag(Xml.Tag tag, P p) {
            Xml.Tag t = (Xml.Tag) super.visitTag(tag, p);
            if (scope.isScope(tag)) {
                Xml.Tag configOptions = t.getChild("configOptions").orElseGet(() -> Xml.Tag.build("<configOptions/>"));
                configOptions = FilterTagChildrenVisitor.filterTagChildren(configOptions,
                        child -> !"useSpringBoot3".equals(child.getName()));
                configOptions = AddOrUpdateChild.addOrUpdateChild(configOptions,
                        Xml.Tag.build("<useSpringBoot4>true</useSpringBoot4>"), getCursor());
                configOptions = AddOrUpdateChild.addOrUpdateChild(configOptions,
                        Xml.Tag.build("<useJackson3>true</useJackson3>"), getCursor());
                t = AddOrUpdateChild.addOrUpdateChild(t, configOptions, getCursor().getParentOrThrow());
            }
            return t;
        }
    }
}

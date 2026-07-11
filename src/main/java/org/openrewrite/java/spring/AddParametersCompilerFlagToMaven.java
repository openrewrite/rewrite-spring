/*
 * Copyright 2026 the original author or authors.
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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.AddPropertyVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.tree.Plugin;
import org.openrewrite.maven.tree.ResolvedPom;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;
import java.util.stream.Stream;

import static org.openrewrite.xml.AddToTagVisitor.addToTag;
import static org.openrewrite.xml.MapTagChildrenVisitor.mapTagChildren;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddParametersCompilerFlagToMaven extends Recipe {

    private static final XPathMatcher PLUGINS_MATCHER = new XPathMatcher("/project/build/plugins");

    @Override
    public String getDisplayName() {
        return "Add `-parameters` compiler flag for Spring";
    }

    @Override
    public String getDescription() {
        return "Sets the `maven.compiler.parameters` property to `true` and, when `kotlin-maven-plugin` " +
                "is declared, configures its `javaParameters` option. " +
                "Spring uses parameter name retention for dependency injection. " +
                "Projects whose effective build already decides parameter name retention — through their own " +
                "configuration or any parent such as `spring-boot-starter-parent` — are not modified.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                if (hasSpringBootStarterParent(document.getRoot())) {
                    return document;
                }
                // Modules with an in-project parent inherit the property from that parent instead
                if (needsCompilerParameters() && !getResolutionResult().parentPomIsProjectPom()) {
                    doAfterVisit(new AddPropertyVisitor("maven.compiler.parameters", "true", false));
                }
                return super.visitDocument(document, ctx);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (PLUGINS_MATCHER.matches(getCursor()) && needsKotlinJavaParameters()) {
                    Optional<Xml.Tag> kotlinPlugin = t.getChildren().stream()
                            .filter(child -> "plugin".equals(child.getName()) &&
                                    "org.jetbrains.kotlin".equals(child.getChildValue("groupId").orElse(null)) &&
                                    "kotlin-maven-plugin".equals(child.getChildValue("artifactId").orElse(null)))
                            .findAny();
                    if (kotlinPlugin.isPresent()) {
                        t = addJavaParametersOption(t, kotlinPlugin.get());
                    }
                }
                return t;
            }

            private boolean needsCompilerParameters() {
                ResolvedPom pom = getResolutionResult().getPom();
                // An existing property, even "false", is an explicit decision to respect
                if (pom.getProperties().containsKey("maven.compiler.parameters")) {
                    return false;
                }
                return effectivePlugins()
                        .filter(p -> "maven-compiler-plugin".equals(p.getArtifactId()) && Plugin.PLUGIN_DEFAULT_GROUPID.equals(p.getGroupId()))
                        .noneMatch(p -> decidesParameters(p.getConfiguration()));
            }

            private boolean needsKotlinJavaParameters() {
                return effectivePlugins()
                        .filter(p -> "kotlin-maven-plugin".equals(p.getArtifactId()) &&
                                "org.jetbrains.kotlin".equals(p.getGroupId()))
                        .noneMatch(p -> decidesJavaParameters(p.getConfiguration()));
            }

            private Stream<Plugin> effectivePlugins() {
                ResolvedPom pom = getResolutionResult().getPom();
                return Stream.concat(pom.getPlugins().stream(), pom.getPluginManagement().stream());
            }

            private Xml.Tag addJavaParametersOption(Xml.Tag plugins, Xml.Tag plugin) {
                Optional<Xml.Tag> maybeConfig = plugin.getChild("configuration");
                if (!maybeConfig.isPresent()) {
                    Xml.Tag updatedPlugin = addToTag(plugin, Xml.Tag.build(
                            "<configuration>\n<javaParameters>true</javaParameters>\n</configuration>"), getCursor());
                    return mapTagChildren(plugins, child -> child == plugin ? updatedPlugin : child);
                }
                Xml.Tag config = maybeConfig.get();
                boolean alreadyDecided = config.getChild("javaParameters").isPresent() ||
                        config.getChild("args")
                                .map(args -> args.getChildren().stream()
                                        .anyMatch(arg -> "-java-parameters".equals(arg.getValue().orElse(null))))
                                .orElse(false);
                if (alreadyDecided) {
                    return plugins;
                }
                Xml.Tag updatedConfig = addToTag(config, Xml.Tag.build("<javaParameters>true</javaParameters>"),
                        new Cursor(getCursor(), plugin));
                Xml.Tag updatedPlugin = mapTagChildren(plugin, child -> child == config ? updatedConfig : child);
                return mapTagChildren(plugins, child -> child == plugin ? updatedPlugin : child);
            }
        };
    }

    /**
     * Raw-XML fallback for when the parent pom cannot be resolved (e.g. offline mode), in which
     * case the effective-model checks cannot see the parent's configuration.
     */
    private static boolean hasSpringBootStarterParent(Xml.Tag root) {
        return root.getChild("parent")
                .flatMap(parent -> parent.getChild("artifactId"))
                .flatMap(Xml.Tag::getValue)
                .map("spring-boot-starter-parent"::equals)
                .orElse(false);
    }

    private static boolean decidesParameters(@Nullable JsonNode config) {
        return config != null &&
                (config.get("parameters") != null ||
                        containsText(config.get("compilerArgs"), "-parameters") ||
                        containsText(config.get("compilerArgument"), "-parameters"));
    }

    private static boolean decidesJavaParameters(@Nullable JsonNode config) {
        return config != null &&
                (config.get("javaParameters") != null ||
                        containsText(config.get("args"), "-java-parameters"));
    }

    private static boolean containsText(@Nullable JsonNode node, String value) {
        if (node == null) {
            return false;
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                if (containsText(child, value)) {
                    return true;
                }
            }
            return false;
        }
        return value.equals(node.asText());
    }
}

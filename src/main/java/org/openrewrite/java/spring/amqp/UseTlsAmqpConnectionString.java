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
package org.openrewrite.java.spring.amqp;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.spring.ChangeSpringPropertyValue;
import org.openrewrite.java.spring.SpringExecutionContextView;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class UseTlsAmqpConnectionString extends Recipe {
    private static final String PREFIX_AMQP = "amqp";
    private static final String PREFIX_AMQP_SECURE = "amqps";

    @Option(displayName = "Property key",
            description = "The Spring property key to perform updates against. " +
                    "If this value is specified, the specified property will be used for searching, otherwise a default of `spring.rabbitmq.addresses` " +
                    "will be used instead.",
            example = "spring.rabbitmq.addresses",
            required = false)
    @Nullable
    String propertyKey;

    @Option(displayName = "Old port",
            description = "The non-TLS enabled port number to replace with the TLS-enabled port. " +
                    "If this value is specified, no changes will be made to amqp connection strings which do not contain this port number. ",
            example = "1234")
    @Nullable
    Integer oldPort;

    @Option(displayName = "TLS port",
            description = "The TLS-enabled port to use.",
            example = "1234")
    @Nullable
    Integer port;

    @Option(displayName = "TLS property key",
            description = "The Spring property key to enable default TLS mode against. " +
                    "If this value is specified, the specified property will be used when updating the default TLS mode, otherwise a default of " +
                    "`spring.rabbitmq.ssl.enabled` will be used instead.",
            example = "spring.rabbitmq.ssl.enabled",
            required = false)
    @Nullable
    String tlsPropertyKey;

    @Option(displayName = "Optional list of file path matcher",
            description = "Each value in this list represents a glob expression that is used to match which files will " +
                    "be modified. If this value is not present, this recipe will query the execution context for " +
                    "reasonable defaults. (\"**/application.yml\", \"**/application.yaml\", and \"**/application.properties\".",
            example = "**/application.yml",
            required = false)
    @Nullable
    List<String> pathExpressions;

    @Override
    public String getDisplayName() {
        return "Use TLS for AMQP connection strings";
    }

    @Override
    public String getDescription() {
        return "Use TLS for AMQP connection strings.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String actualPropertyKey = propertyKey == null || propertyKey.isEmpty() ? "spring.rabbitmq.addresses" : propertyKey;
        String actualTlsPropertyKey = tlsPropertyKey == null || tlsPropertyKey.isEmpty() ? "spring.rabbitmq.ssl.enabled" : tlsPropertyKey;
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof Yaml.Documents || sourceFile instanceof Properties.File;
            }

            @Override
            public Tree visit(@Nullable Tree t, ExecutionContext ctx) {
                if (t instanceof Yaml.Documents && sourcePathMatches(((SourceFile) t).getSourcePath(), ctx)) {
                    t = new UseTlsAmqpConnectionStringYaml(actualPropertyKey, oldPort, port, actualTlsPropertyKey, pathExpressions)
                        .getVisitor().visit(t, ctx);
                } else if (t instanceof Properties.File && sourcePathMatches(((SourceFile) t).getSourcePath(), ctx)) {
                    t = new UseTlsAmqpConnectionStringProperties(actualPropertyKey, oldPort, port, actualTlsPropertyKey, pathExpressions)
                        .getVisitor().visit(t, ctx);
                }
                return t;
            }

            private boolean sourcePathMatches(Path sourcePath, ExecutionContext ctx) {
                List<String> expressions = pathExpressions;
                if (expressions == null || pathExpressions.isEmpty()) {
                    //If not defined, get reasonable defaults from the execution context.
                    expressions = SpringExecutionContextView.view(ctx).getDefaultApplicationConfigurationPaths();
                }
                if (expressions.isEmpty()) {
                    return true;
                }
                for (String filePattern : expressions) {
                    if (PathUtils.matchesGlob(sourcePath, filePattern)) {
                        return true;
                    }
                }

                return false;
            }
        };
    }

    @EqualsAndHashCode(callSuper = false)
    @Value
    static class UseTlsAmqpConnectionStringYaml {
        String propertyKey;

        @Nullable
        Integer oldPort;

        @Nullable
        Integer port;

        String tlsPropertyKey;

        @Nullable
        List<String> pathExpressions;

        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return Preconditions.check(new YamlVisitor<ExecutionContext>() {
                @Override
                public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                    if (!FindProperty.find(documents, propertyKey, true).isEmpty()) {
                        return SearchResult.found(documents);
                    }
                    return documents;
                }
            }, new YamlIsoVisitor<ExecutionContext>() {
                final JsonPathMatcher amqpUrl = new JsonPathMatcher("$." + propertyKey);

                @Override
                public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                    Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);

                    if (amqpUrl.matches(getCursor())) {
                        String[] connectionStrings = ((Yaml.Scalar) e.getValue()).getValue().split(",");
                        try {
                            boolean skip = false;
                            boolean updated = false;
                            for (int i = 0; i < connectionStrings.length; i++) {
                                String connectionString = connectionStrings[i];
                                if (connectionString.startsWith(PREFIX_AMQP + "://") || connectionString.startsWith(PREFIX_AMQP_SECURE + "://")) {
                                    // amqp(s)://hostname:port(/virtualhost)
                                    URI amqpUrl = URI.create(connectionString);
                                    if (oldPort != null && !amqpUrl.getSchemeSpecificPart().contains(":" + oldPort)) {
                                        skip = true;
                                        continue;
                                    }
                                    URI updatedAmqpUrl = maybeUpdateAmqpConnectionUri(amqpUrl, port);
                                    if (updatedAmqpUrl != amqpUrl) {
                                        updated = true;
                                        connectionStrings[i] = updatedAmqpUrl.toString();
                                        doAfterVisit(new ChangeSpringPropertyValue(tlsPropertyKey, "true", "false", null, null)
                                            .getVisitor());
                                    }
                                } else {
                                    // hostname:port(/virtualhost)
                                    String[] parts = connectionString.split(":");
                                    if (parts.length == 2 && oldPort != null && !parts[1].split("/")[0].equals(oldPort.toString())) {
                                        skip = true;
                                        continue;
                                    }
                                    String updatedConnectionString = maybeUpdateAmqpConnectionString(connectionString, port);
                                    if (!updatedConnectionString.equals(connectionString)) {
                                        updated = true;
                                        connectionStrings[i] = updatedConnectionString;
                                        doAfterVisit(new AddSpringProperty(tlsPropertyKey, "true", null, pathExpressions)
                                            .getVisitor());
                                        doAfterVisit(new ChangeSpringPropertyValue(tlsPropertyKey, "true", null, null, null)
                                            .getVisitor());
                                    }
                                }
                            }

                            if (skip && !updated) {
                                return e;
                            }

                            if (updated) {
                                e = e.withValue(((Yaml.Scalar) e.getValue()).withValue(String.join(",", connectionStrings)));
                            }
                        } catch (URISyntaxException | IllegalArgumentException ignored) {
                            // do nothing
                        }
                    }
                    return e;
                }
            });
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Value
    static class UseTlsAmqpConnectionStringProperties {
        String propertyKey;

        @Nullable
        Integer oldPort;

        @Nullable
        Integer port;

        String tlsPropertyKey;

        @Nullable
        List<String> pathExpressions;

        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return Preconditions.check(new PropertiesVisitor<ExecutionContext>() {
                @Override
                public Properties visitFile(Properties.File file, ExecutionContext ctx) {
                    if (!FindProperties.find(file, propertyKey, true).isEmpty()) {
                        return SearchResult.found(file);
                    }
                    return file;
                }
            }, new PropertiesIsoVisitor<ExecutionContext>() {
                @Override
                public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                    Properties.Entry e = super.visitEntry(entry, ctx);

                    if (NameCaseConvention.equalsRelaxedBinding(entry.getKey(), propertyKey)) {
                        String[] connectionStrings = entry.getValue().getText().split(",");
                        try {
                            boolean skip = false;
                            boolean updated = false;
                            for (int i = 0; i < connectionStrings.length; i++) {
                                String connectionString = connectionStrings[i];
                                if (connectionString.startsWith(PREFIX_AMQP + "://") || connectionString.startsWith(PREFIX_AMQP_SECURE + "://")) {
                                    // amqp(s)://hostname:port(/virtualhost)
                                    URI amqpUrl = URI.create(connectionStrings[i]);
                                    if (oldPort != null && !amqpUrl.getSchemeSpecificPart().contains(":" + oldPort)) {
                                        skip = true;
                                        continue;
                                    }
                                    URI updatedAmqpUrl = maybeUpdateAmqpConnectionUri(amqpUrl, port);
                                    if (updatedAmqpUrl != amqpUrl) {
                                        updated = true;
                                        connectionStrings[i] = updatedAmqpUrl.toString();
                                        doAfterVisit(new ChangeSpringPropertyValue(tlsPropertyKey, "true", "false", null, null)
                                            .getVisitor());
                                    }
                                } else {
                                    // hostname:port(/virtualhost)
                                    String[] parts = connectionString.split(":");
                                    if (parts.length == 2 && oldPort != null && !parts[1].split("/")[0].equals(oldPort.toString())) {
                                        skip = true;
                                        continue;
                                    }
                                    String updatedConnectionString = maybeUpdateAmqpConnectionString(connectionString, port);
                                    if (!updatedConnectionString.equals(connectionString)) {
                                        updated = true;
                                        connectionStrings[i] = updatedConnectionString;
                                        doAfterVisit(new AddSpringProperty(tlsPropertyKey, "true", null, pathExpressions)
                                            .getVisitor());
                                        doAfterVisit(new ChangeSpringPropertyValue(tlsPropertyKey, "true", null, null, null)
                                            .getVisitor());
                                    }
                                }
                            }

                            if (skip && !updated) {
                                return e;
                            }

                            if (updated) {
                                e = e.withValue(e.getValue().withText(String.join(",", connectionStrings)));
                            }
                        } catch (URISyntaxException | IllegalArgumentException ignored) {
                            // do nothing
                        }
                    }
                    return e;
                }
            });
        }
    }

    private static URI maybeUpdateAmqpConnectionUri(URI amqpUrl, @Nullable Integer port) throws URISyntaxException {
        URI updatedAmqpUrl = amqpUrl;
        if (port != null && !amqpUrl.getSchemeSpecificPart().contains(":" + port)) {
            updatedAmqpUrl = new URI(amqpUrl.getScheme(), amqpUrl.getSchemeSpecificPart()
                    .replaceFirst(":\\d+", ":" + port), amqpUrl.getFragment());
        }
        if (PREFIX_AMQP.equals(amqpUrl.getScheme())) {
            updatedAmqpUrl = new URI(PREFIX_AMQP_SECURE, updatedAmqpUrl.getSchemeSpecificPart(), amqpUrl.getFragment());
        }
        return updatedAmqpUrl;
    }

    private static String maybeUpdateAmqpConnectionString(String amqpUrl, @Nullable Integer port) {
        String updatedAmqpUrl = amqpUrl;
        if (port != null && !amqpUrl.contains(":" + port)) {
            updatedAmqpUrl = updatedAmqpUrl.replaceFirst(":\\d+", ":" + port);
        }
        return updatedAmqpUrl;
    }
}

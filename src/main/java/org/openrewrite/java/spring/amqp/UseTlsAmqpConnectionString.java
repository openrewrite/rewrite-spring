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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.internal.StringUtils;
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

import java.nio.file.Path;
import java.util.ArrayList;
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
            example = "1234",
            required = false)
    @Nullable
    Integer oldPort;

    @Option(displayName = "TLS port",
            description = "The TLS-enabled port to use.",
            example = "1234",
            required = false)
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

    String displayName = "Use TLS for AMQP connection strings";

    String description = "Use TLS for AMQP connection strings.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String actualPropertyKey = StringUtils.isNullOrEmpty(propertyKey) ? "spring.rabbitmq.addresses" : propertyKey;
        String actualTlsPropertyKey = StringUtils.isNullOrEmpty(tlsPropertyKey) ? "spring.rabbitmq.ssl.enabled" : tlsPropertyKey;
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
                        if (e.getValue() instanceof Yaml.Scalar) {
                            e = e.withValue(updateScalar((Yaml.Scalar) e.getValue()));
                        } else if (e.getValue() instanceof Yaml.Sequence) {
                            // spring.rabbitmq.addresses is a List<String>, so a YAML sequence is valid too
                            Yaml.Sequence sequence = (Yaml.Sequence) e.getValue();
                            e = e.withValue(sequence.withEntries(ListUtils.map(sequence.getEntries(), item ->
                                    item.getBlock() instanceof Yaml.Scalar ?
                                            item.withBlock(updateScalar((Yaml.Scalar) item.getBlock())) : item)));
                        }
                    }
                    return e;
                }

                private Yaml.Scalar updateScalar(Yaml.Scalar scalar) {
                    UpdatedAddresses updated = updateAddresses(scalar.getValue(), oldPort, port);
                    if (updated == null) {
                        return scalar;
                    }
                    tlsPropertyVisitors(updated, tlsPropertyKey, pathExpressions).forEach(this::doAfterVisit);
                    return scalar.withValue(updated.getAddresses());
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
                        UpdatedAddresses updated = updateAddresses(entry.getValue().getText(), oldPort, port);
                        if (updated != null) {
                            tlsPropertyVisitors(updated, tlsPropertyKey, pathExpressions).forEach(this::doAfterVisit);
                            e = e.withValue(e.getValue().withText(updated.getAddresses()));
                        }
                    }
                    return e;
                }
            });
        }
    }

    @Value
    private static class UpdatedAddresses {
        String addresses;
        boolean uriUpdated;
        boolean hostAndPortUpdated;
    }

    /**
     * Updates a comma-separated list of addresses, returning {@code null} when none of them changed.
     */
    private static @Nullable UpdatedAddresses updateAddresses(String addresses, @Nullable Integer oldPort, @Nullable Integer port) {
        String[] parts = addresses.split(",");
        boolean uriUpdated = false;
        boolean hostAndPortUpdated = false;
        for (int i = 0; i < parts.length; i++) {
            String updated = updateAddress(parts[i], oldPort, port);
            if (!updated.equals(parts[i])) {
                if (isUri(parts[i])) {
                    uriUpdated = true;
                } else {
                    hostAndPortUpdated = true;
                }
                parts[i] = updated;
            }
        }
        return uriUpdated || hostAndPortUpdated ?
                new UpdatedAddresses(String.join(",", parts), uriUpdated, hostAndPortUpdated) :
                null;
    }

    private static List<TreeVisitor<?, ExecutionContext>> tlsPropertyVisitors(UpdatedAddresses updated, String tlsPropertyKey,
                                                                            @Nullable List<String> pathExpressions) {
        List<TreeVisitor<?, ExecutionContext>> visitors = new ArrayList<>();
        if (updated.isUriUpdated()) {
            visitors.add(new ChangeSpringPropertyValue(tlsPropertyKey, "true", "false", null, null).getVisitor());
        }
        if (updated.isHostAndPortUpdated()) {
            visitors.add(new AddSpringProperty(tlsPropertyKey, "true", null, pathExpressions).getVisitor());
            visitors.add(new ChangeSpringPropertyValue(tlsPropertyKey, "true", null, null, null).getVisitor());
        }
        return visitors;
    }

    private static boolean isUri(String address) {
        return address.startsWith(PREFIX_AMQP + "://") || address.startsWith(PREFIX_AMQP_SECURE + "://");
    }

    /**
     * Updates one address of the form {@code [amqp[s]://][username[:password]@]host[:port][/virtualhost]},
     * comparing and replacing the whole port, so neither a password nor a longer port is mistaken for it.
     */
    private static String updateAddress(String address, @Nullable Integer oldPort, @Nullable Integer port) {
        int authorityStart = isUri(address) ? address.indexOf("://") + 3 : 0;
        int authorityEnd = authorityStart;
        while (authorityEnd < address.length() && "/?#".indexOf(address.charAt(authorityEnd)) < 0) {
            authorityEnd++;
        }
        int hostStart = Math.max(authorityStart, address.lastIndexOf('@', authorityEnd - 1) + 1);
        int portSeparator = address.lastIndexOf(':', authorityEnd - 1);
        String currentPort = null;
        // a ':' inside an IPv6 literal such as [::1] is not a port separator
        if (portSeparator >= hostStart && address.lastIndexOf(']', authorityEnd - 1) < portSeparator) {
            String digits = address.substring(portSeparator + 1, authorityEnd);
            if (digits.matches("\\d+")) {
                currentPort = digits;
            }
        }

        if (oldPort != null && !oldPort.toString().equals(currentPort)) {
            return address;
        }
        String updated = address;
        if (port != null && currentPort != null && !port.toString().equals(currentPort)) {
            updated = address.substring(0, portSeparator + 1) + port + address.substring(authorityEnd);
        }
        if (updated.startsWith(PREFIX_AMQP + "://")) {
            updated = PREFIX_AMQP_SECURE + updated.substring(PREFIX_AMQP.length());
        }
        return updated;
    }
}

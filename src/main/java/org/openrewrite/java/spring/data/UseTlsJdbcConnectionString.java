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
package org.openrewrite.java.spring.data;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.internal.lang.Nullable;
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

@Value
@EqualsAndHashCode(callSuper = false)
public class UseTlsJdbcConnectionString extends Recipe {
    @Option(
            displayName = "Property key",
            description = "The Spring property key to perform updates against. " +
                    "If this value is specified, the specified property will be used for searching, otherwise a default of `spring.datasource.url` " +
                    "will be used instead.",
            example = "spring.datasource.url"
    )
    @Nullable
    String propertyKey;

    @Option(
            displayName = "Old Port",
            description = "The non-TLS enabled port number to replace with the TLS-enabled port. " +
                    "If this value is specified, no changes will be made to jdbc connection strings which do not contain this port number. ",
            example = "1234")
    @Nullable
    Integer oldPort;

    @Option(
            displayName = "TLS Port",
            description = "The TLS-enabled port to use.",
            example = "1234")
    @Nullable
    Integer port;

    @Option(
            displayName = "Connection attribute",
            description = "A connection attribute, if any, indicating to the JDBC " +
                          "provider that this is a TLS connection.",
            example = "sslConnection=true")
    @Nullable
    String attribute;

    @Override
    public String getDisplayName() {
        return "Use TLS for JDBC connection strings";
    }

    @Override
    public String getDescription() {
        return "Increasingly, for compliance reasons (e.g. [NACHA](https://www.nacha.org/sites/default/files/2022-06/End_User_Briefing_Supplementing_Data_Security_UPDATED_FINAL.pdf)), JDBC connection strings " +
               "should be TLS-enabled. This recipe will update the port and " +
               "optionally add a connection attribute to indicate that the " +
               "connection is TLS-enabled.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        String attr = attribute;
        if(attr != null) {
            attr = attr.trim();
            if(!attr.endsWith(";")) {
                attr = attr + ';';
            }
        }
        final String validatedAttribute = attr;

        final String actualPropertyKey = propertyKey == null ? "spring.datasource.url" : propertyKey;
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext executionContext) {
                return sourceFile instanceof Yaml.Documents || sourceFile instanceof Properties.File;
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                doNext(new UseTlsJdbcConnectionStringYaml(actualPropertyKey, oldPort, port, validatedAttribute));
                doNext(new UseTlsJdbcConnectionStringProperties(actualPropertyKey, oldPort, port, validatedAttribute));
                return tree;
            }
        };
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class UseTlsJdbcConnectionStringYaml extends Recipe {
        String propertyKey;

        @Nullable
        Integer oldPort;

        @Nullable
        Integer port;

        @Nullable
        String attribute;

        @Override
        public String getDisplayName() {
            return "Use TLS for JDBC connection strings";
        }

        @Override
        protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
            return new YamlVisitor<ExecutionContext>() {
                @Override
                public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                    if (!FindProperty.find(documents, propertyKey, true).isEmpty()) {
                        return SearchResult.found(documents);
                    }
                    return documents;
                }
            };
        }

        @Override
        protected TreeVisitor<?, ExecutionContext> getVisitor() {
            return new YamlIsoVisitor<ExecutionContext>() {
                final JsonPathMatcher jdbcUrl = new JsonPathMatcher("$." + propertyKey);

                @Override
                public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                    Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);

                    if (jdbcUrl.matches(getCursor())) {
                        String connectionString = ((Yaml.Scalar) e.getValue()).getValue();
                        try {
                            URI jdbcUrl = URI.create(connectionString);
                            if (oldPort != null && !jdbcUrl.getSchemeSpecificPart().contains(":" + oldPort + "/")) {
                                return e;
                            }
                            URI updatedJdbcUrl = maybeUpdateJdbcConnectionString(jdbcUrl, port, attribute);

                            if (updatedJdbcUrl != jdbcUrl) {
                                e = e.withValue(((Yaml.Scalar) e.getValue()).withValue(updatedJdbcUrl.toString()));
                            }
                        } catch (URISyntaxException | IllegalArgumentException ignored) {
                            // do nothing
                        }
                    }
                    return e;
                }
            };
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class UseTlsJdbcConnectionStringProperties extends Recipe {
        String propertyKey;

        @Nullable
        Integer oldPort;

        @Nullable
        Integer port;

        @Nullable
        String attribute;

        @Override
        public String getDisplayName() {
            return "Use TLS for JDBC connection strings";
        }

        @Override
        protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
            return new PropertiesVisitor<ExecutionContext>() {
                @Override
                public Properties visitFile(Properties.File file, ExecutionContext ctx) {
                    if (!FindProperties.find(file, propertyKey, true).isEmpty()) {
                        return SearchResult.found(file);
                    }
                    return file;
                }
            };
        }

        @Override
        protected TreeVisitor<?, ExecutionContext> getVisitor() {
            return new PropertiesIsoVisitor<ExecutionContext>() {
                @Override
                public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                    Properties.Entry e = super.visitEntry(entry, ctx);

                    if (NameCaseConvention.equalsRelaxedBinding(entry.getKey(), propertyKey)) {
                        String connectionString = entry.getValue().getText();
                        try {
                            URI jdbcUrl = URI.create(connectionString);
                            if (oldPort != null && !jdbcUrl.getSchemeSpecificPart().contains(":" + oldPort + "/")) {
                                return e;
                            }
                            URI updatedJdbcUrl = maybeUpdateJdbcConnectionString(jdbcUrl, port, attribute);

                            if (updatedJdbcUrl != jdbcUrl) {
                                e = e.withValue(e.getValue().withText(updatedJdbcUrl.toString()));
                            }
                        } catch (URISyntaxException | IllegalArgumentException ignored) {
                            // do nothing
                        }
                    }
                    return e;
                }
            };
        }
    }

    private static URI maybeUpdateJdbcConnectionString(URI jdbcUrl, @Nullable Integer port, @Nullable String validatedAttribute) throws URISyntaxException {
        URI updatedJdbcUrl = jdbcUrl;
        if (port != null && !jdbcUrl.getSchemeSpecificPart().contains(":" + port + "/")) {
            updatedJdbcUrl = new URI(jdbcUrl.getScheme(), jdbcUrl.getSchemeSpecificPart()
                    .replaceFirst(":\\d+/", ":" + port + "/"), jdbcUrl.getFragment());
        }
        if (validatedAttribute != null && !jdbcUrl.getSchemeSpecificPart().contains(validatedAttribute)) {
            updatedJdbcUrl = new URI(updatedJdbcUrl.getScheme(),
                    updatedJdbcUrl.getSchemeSpecificPart() +
                            (updatedJdbcUrl.getSchemeSpecificPart().endsWith(";") ? "" : ":") +
                            validatedAttribute,
                    updatedJdbcUrl.getFragment());
        }
        return updatedJdbcUrl;
    }
}

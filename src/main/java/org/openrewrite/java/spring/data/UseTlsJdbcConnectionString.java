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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.net.URI;
import java.net.URISyntaxException;

@Value
@EqualsAndHashCode(callSuper = false)
public class UseTlsJdbcConnectionString extends Recipe {

    @Option(
            displayName = "Port",
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
        return "Increasingly, for compliance reasons, JDBC connection strings " +
               "should be TLS-enabled. This recipe will update the port and " +
               "optionally add a connection attribute to indicate that the " +
               "connection is TLS-enabled.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            final JsonPathMatcher jdbcUrl = new JsonPathMatcher("$.spring.datasource.url");

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);

                if (jdbcUrl.matches(getCursor())) {
                    String connectionString = ((Yaml.Scalar) e.getValue()).getValue();
                    try {
                        URI jdbcUrl = URI.create(connectionString);
                        URI updatedJdbcUrl = jdbcUrl;
                        if (port != null && !jdbcUrl.getSchemeSpecificPart().contains(":" + port + "/")) {
                            updatedJdbcUrl = new URI(jdbcUrl.getScheme(), jdbcUrl.getSchemeSpecificPart()
                                    .replaceFirst(":\\d+/", ":" + port + "/"), jdbcUrl.getFragment());
                        }
                        if (attribute != null && !jdbcUrl.getSchemeSpecificPart().contains(attribute)) {
                            updatedJdbcUrl = new URI(updatedJdbcUrl.getScheme(),
                                    updatedJdbcUrl.getSchemeSpecificPart() +
                                    (updatedJdbcUrl.getSchemeSpecificPart().endsWith(";") ? "" : ":") +
                                    attribute + ";",
                                    updatedJdbcUrl.getFragment());
                        }

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

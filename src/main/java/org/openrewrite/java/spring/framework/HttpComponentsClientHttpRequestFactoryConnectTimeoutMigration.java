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
package org.openrewrite.java.spring.framework;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.*;

public class HttpComponentsClientHttpRequestFactoryConnectTimeoutMigration extends Recipe {
    private static final String POOLING_CONNECTION_MANAGER = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager";
    private static final String CLOSEABLE_HTTP_CLIENT = "org.apache.hc.client5.http.impl.classic.CloseableHttpClient";
    private static final String REQUEST_FACTORY = "org.springframework.http.client.HttpComponentsClientHttpRequestFactory";
    private static final String CONNECTION_CONFIG = "org.apache.hc.client5.http.config.ConnectionConfig";
    private static final String TIMEOUT = "org.apache.hc.core5.util.Timeout";

    private static final MethodMatcher SET_CONNECT_TIMEOUT = new MethodMatcher(REQUEST_FACTORY + " setConnectTimeout(int)");
    private static final MethodMatcher SET_CONNECTION_MANAGER = new MethodMatcher("org.apache.hc.client5.http.impl.classic.HttpClientBuilder setConnectionManager(..)");

    // Known case we do not handle yet
    private static final MethodMatcher SET_DEFAULT_CONNECTION_CONFIG = new MethodMatcher(POOLING_CONNECTION_MANAGER + " setDefaultConnectionConfig(..)", true);

    @Getter
    final String displayName = "Migrate `setConnectTimeout(int)` to a locally wired ConnectionConfig";

    @Getter
    final String description = "Migrates `setConnectTimeout(int)` to the Apache HttpClient `ConnectionConfig` when the local " +
                               "`PoolingHttpClientConnectionManager` is used by the `HttpComponentsClientHttpRequestFactory`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(new UsesMethod<>(SET_CONNECT_TIMEOUT), new UsesType<>(POOLING_CONNECTION_MANAGER, true)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);

                        Map<String, J.VariableDeclarations> managers = new HashMap<>();
                        Map<String, String> wiredTo = new HashMap<>();
                        Set<String> configuredManagers = new HashSet<>();
                        for (Statement statement : b.getStatements()) {
                            if (statement instanceof J.VariableDeclarations) {
                                J.VariableDeclarations declaration = (J.VariableDeclarations) statement;
                                J.VariableDeclarations.NamedVariable variable = declaration.getVariables().get(0);
                                String name = variable.getName().getSimpleName();
                                JavaType.FullyQualified type = declaration.getTypeAsFullyQualified();
                                if (TypeUtils.isAssignableTo(POOLING_CONNECTION_MANAGER, type)) {
                                    managers.put(name, declaration);
                                } else if (TypeUtils.isAssignableTo(CLOSEABLE_HTTP_CLIENT, type)) {
                                    String managerName = connectionManagerName(variable.getInitializer());
                                    if (managerName != null) {
                                        wiredTo.put(name, managerName);
                                    }
                                } else if (TypeUtils.isAssignableTo(REQUEST_FACTORY, type) &&
                                           variable.getInitializer() instanceof J.NewClass) {
                                    List<Expression> arguments = ((J.NewClass) variable.getInitializer()).getArguments();
                                    if (arguments.size() == 1 && arguments.get(0) instanceof J.Identifier) {
                                        wiredTo.put(name, ((J.Identifier) arguments.get(0)).getSimpleName());
                                    }
                                }
                            } else if (statement instanceof J.MethodInvocation) {
                                J.MethodInvocation invocation = (J.MethodInvocation) statement;
                                if (SET_DEFAULT_CONNECTION_CONFIG.matches(invocation) && invocation.getSelect() instanceof J.Identifier) {
                                    configuredManagers.add(((J.Identifier) invocation.getSelect()).getSimpleName());
                                }
                            }
                        }

                        // Only the first `setConnectTimeout` per connection manager can be migrated; later ones keep the TODO
                        Map<J.VariableDeclarations, Expression> migrations = new LinkedHashMap<>();
                        Set<UUID> migrated = new HashSet<>();
                        for (Statement statement : b.getStatements()) {
                            if (!(statement instanceof J.MethodInvocation)) {
                                continue;
                            }
                            J.MethodInvocation timeout = (J.MethodInvocation) statement;
                            if (!SET_CONNECT_TIMEOUT.matches(timeout) || !(timeout.getSelect() instanceof J.Identifier)) {
                                continue;
                            }
                            String clientName = wiredTo.get(((J.Identifier) timeout.getSelect()).getSimpleName());
                            String managerName = wiredTo.get(clientName);
                            J.VariableDeclarations manager = managers.get(managerName);
                            if (manager != null && !configuredManagers.contains(managerName) &&
                                migrations.putIfAbsent(manager, timeout.getArguments().get(0)) == null) {
                                migrated.add(timeout.getId());
                            }
                        }

                        if (migrations.isEmpty()) {
                            return b;
                        }

                        maybeAddImport(CONNECTION_CONFIG);
                        maybeAddImport(TIMEOUT);
                        JavaTemplate template = JavaTemplate
                                .builder("#{any(" + POOLING_CONNECTION_MANAGER + ")}.setDefaultConnectionConfig(" +
                                         "ConnectionConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(#{any(int)})).build());")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                                .imports(CONNECTION_CONFIG, TIMEOUT)
                                .build();
                        for (Map.Entry<J.VariableDeclarations, Expression> migration : migrations.entrySet()) {
                            J.VariableDeclarations manager = migration.getKey();
                            b = template.apply(new Cursor(getCursor().getParentOrThrow(), b), manager.getCoordinates().after(),
                                    manager.getVariables().get(0).getName().withPrefix(Space.EMPTY), migration.getValue());
                        }
                        return b.withStatements(ListUtils.map(b.getStatements(),
                                statement -> migrated.contains(statement.getId()) ? null : statement));
                    }

                    private @Nullable String connectionManagerName(@Nullable Expression expression) {
                        if (!(expression instanceof J.MethodInvocation)) {
                            return null;
                        }
                        J.MethodInvocation invocation = (J.MethodInvocation) expression;
                        if (SET_CONNECTION_MANAGER.matches(invocation) && invocation.getArguments().get(0) instanceof J.Identifier) {
                            return ((J.Identifier) invocation.getArguments().get(0)).getSimpleName();
                        }
                        return connectionManagerName(invocation.getSelect());
                    }
                });
    }
}

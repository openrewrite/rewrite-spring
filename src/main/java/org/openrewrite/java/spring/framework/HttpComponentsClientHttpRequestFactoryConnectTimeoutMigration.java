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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.*;

public class HttpComponentsClientHttpRequestFactoryConnectTimeoutMigration extends Recipe {
    private static final String POOLING_CONNECTION_MANAGER = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager";
    private static final MethodMatcher SET_CONNECT_TIMEOUT = new MethodMatcher("org.springframework.http.client.HttpComponentsClientHttpRequestFactory setConnectTimeout(int)");

    @Getter
    final String displayName = "Migrate `setConnectTimeout(int)` to ConnectionConfig `setConnectTimeout(..)`";

    @Getter
    final String description = "Migrates `setConnectTimeout(int)` to the Apache HttpClient `ConnectionConfig` when the local " +
                               "`PoolingHttpClientConnectionManager` is used by the `HttpComponentsClientHttpRequestFactory`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SET_CONNECT_TIMEOUT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                Map<String, J.VariableDeclarations> managers = new HashMap<>();
                Map<String, String> clients = new HashMap<>();
                Map<String, String> factories = new HashMap<>();

                for (Statement statement : b.getStatements()) {
                    if (!(statement instanceof J.VariableDeclarations)) {
                        continue;
                    }
                    J.VariableDeclarations declaration = (J.VariableDeclarations) statement;
                    String variableName = declaration.getVariables().get(0).getName().getSimpleName();
                    Expression initializer = declaration.getVariables().get(0).getInitializer();
                    if (TypeUtils.isAssignableTo(POOLING_CONNECTION_MANAGER, declaration.getTypeAsFullyQualified())) {
                        managers.put(variableName, declaration);
                    } else if (initializer != null && TypeUtils.isAssignableTo("org.apache.hc.client5.http.impl.classic.CloseableHttpClient", declaration.getTypeAsFullyQualified())) {
                        String managerName = connectionManagerName(initializer);
                        if (managerName != null) {
                            clients.put(variableName, managerName);
                        }
                    } else if (initializer instanceof J.NewClass && TypeUtils.isAssignableTo("org.springframework.http.client.HttpComponentsClientHttpRequestFactory", declaration.getTypeAsFullyQualified())) {
                        J.NewClass newClass = (J.NewClass) initializer;
                        if (newClass.getArguments().size() == 1 && newClass.getArguments().get(0) instanceof J.Identifier) {
                            factories.put(variableName, ((J.Identifier) newClass.getArguments().get(0)).getSimpleName());
                        }
                    }
                }

                Map<UUID, J.VariableDeclarations> migrations = new LinkedHashMap<>();
                Set<String> configuredManagers = new HashSet<>();
                for (Statement statement : b.getStatements()) {
                    if (!(statement instanceof J.MethodInvocation) || !SET_CONNECT_TIMEOUT.matches((J.MethodInvocation) statement)) {
                        continue;
                    }
                    J.MethodInvocation timeout = (J.MethodInvocation) statement;
                    if (!(timeout.getSelect() instanceof J.Identifier)) {
                        continue;
                    }
                    String clientName = factories.get(((J.Identifier) timeout.getSelect()).getSimpleName());
                    String managerName = clients.get(clientName);
                    J.VariableDeclarations manager = managers.get(managerName);
                    if (manager != null && configuredManagers.add(managerName) && !hasDefaultConnectionConfig(b, managerName)) {
                        migrations.put(timeout.getId(), manager);
                    }
                }

                for (Map.Entry<UUID, J.VariableDeclarations> migration : migrations.entrySet()) {
                    J.MethodInvocation timeout = findTimeout(b, migration.getKey());
                    if (timeout == null) {
                        continue;
                    }
                    maybeAddImport("org.apache.hc.client5.http.config.ConnectionConfig");
                    maybeAddImport("org.apache.hc.core5.util.Timeout");
                    b = JavaTemplate.builder("#{any()}.setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(#{any(int)})).build());")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports("org.apache.hc.client5.http.config.ConnectionConfig", "org.apache.hc.core5.util.Timeout")
                            .build()
                            .apply(getCursor(), migration.getValue().getCoordinates().after(),
                                    migration.getValue().getVariables().get(0).getName().withPrefix(Space.EMPTY),
                                    timeout.getArguments().get(0));
                }

                if (migrations.isEmpty()) {
                    return b;
                }
                return (J.Block) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (migrations.containsKey(method.getId())) {
                            //noinspection DataFlowIssue
                            return null;
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                }.visitNonNull(b, ctx);
            }

            private String connectionManagerName(Expression expression) {
                if (!(expression instanceof J.MethodInvocation)) {
                    return null;
                }
                J.MethodInvocation invocation = (J.MethodInvocation) expression;
                if ("setConnectionManager".equals(invocation.getSimpleName()) &&
                    invocation.getArguments().size() == 1 && invocation.getArguments().get(0) instanceof J.Identifier) {
                    return ((J.Identifier) invocation.getArguments().get(0)).getSimpleName();
                }
                return invocation.getSelect() == null ? null : connectionManagerName(invocation.getSelect());
            }

            private boolean hasDefaultConnectionConfig(J.Block block, String managerName) {
                for (Statement statement : block.getStatements()) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation invocation = (J.MethodInvocation) statement;
                        if ("setDefaultConnectionConfig".equals(invocation.getSimpleName()) &&
                            invocation.getSelect() instanceof J.Identifier &&
                            managerName.equals(((J.Identifier) invocation.getSelect()).getSimpleName())) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private J.MethodInvocation findTimeout(J.Block block, UUID id) {
                for (Statement statement : block.getStatements()) {
                    if (statement instanceof J.MethodInvocation && id.equals(((J.MethodInvocation) statement).getId())) {
                        return (J.MethodInvocation) statement;
                    }
                }
                return null;
            }
        });
    }
}

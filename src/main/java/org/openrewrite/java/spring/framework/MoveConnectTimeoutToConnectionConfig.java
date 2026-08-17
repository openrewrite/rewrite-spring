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
import java.util.concurrent.atomic.AtomicBoolean;

public class MoveConnectTimeoutToConnectionConfig extends Recipe {
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
    final String displayName = "Move `setConnectTimeout(int)` to a locally wired `ConnectionConfig`";

    @Getter
    final String description = "Moves `setConnectTimeout(int)` to the Apache HttpClient `ConnectionConfig` when the local " +
                               "`PoolingHttpClientConnectionManager` is used by the `HttpComponentsClientHttpRequestFactory`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(new UsesMethod<>(SET_CONNECT_TIMEOUT), new UsesType<>(POOLING_CONNECTION_MANAGER, true)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);

                        Map<String, J.VariableDeclarations.NamedVariable> managerVariables = new HashMap<>();
                        Map<String, J.VariableDeclarations> managerDeclarations = new HashMap<>();
                        Map<String, String> clientToManager = new HashMap<>();
                        Map<String, String> factoryToManager = new HashMap<>();
                        Set<String> configuredManagers = new HashSet<>();
                        for (Statement statement : b.getStatements()) {
                            if (statement instanceof J.VariableDeclarations) {
                                J.VariableDeclarations declaration = (J.VariableDeclarations) statement;
                                JavaType.FullyQualified type = declaration.getTypeAsFullyQualified();
                                for (J.VariableDeclarations.NamedVariable variable : declaration.getVariables()) {
                                    String name = variable.getName().getSimpleName();
                                    // Only a locally constructed manager is known to not carry a `ConnectionConfig` yet
                                    if (TypeUtils.isAssignableTo(POOLING_CONNECTION_MANAGER, type) &&
                                        variable.getInitializer() instanceof J.NewClass) {
                                        managerVariables.put(name, variable);
                                        managerDeclarations.put(name, declaration);
                                    } else if (TypeUtils.isAssignableTo(CLOSEABLE_HTTP_CLIENT, type)) {
                                        String managerName = connectionManagerName(variable.getInitializer());
                                        if (managerName != null) {
                                            clientToManager.put(name, managerName);
                                        }
                                    } else if (TypeUtils.isAssignableTo(REQUEST_FACTORY, type) &&
                                               variable.getInitializer() instanceof J.NewClass) {
                                        List<Expression> arguments = ((J.NewClass) variable.getInitializer()).getArguments();
                                        if (arguments.size() == 1) {
                                            Expression arg = arguments.get(0);
                                            String managerName = null;
                                            if (arg instanceof J.Identifier) {
                                                managerName = clientToManager.get(((J.Identifier) arg).getSimpleName());
                                            } else {
                                                managerName = connectionManagerName(arg);
                                            }
                                            if (managerName != null) {
                                                factoryToManager.put(name, managerName);
                                            }
                                        }
                                    }
                                }
                            } else if (statement instanceof J.MethodInvocation) {
                                J.MethodInvocation invocation = (J.MethodInvocation) statement;
                                if (SET_DEFAULT_CONNECTION_CONFIG.matches(invocation) && invocation.getSelect() instanceof J.Identifier) {
                                    configuredManagers.add(((J.Identifier) invocation.getSelect()).getSimpleName());
                                }
                            }
                        }

                        // Group setConnectTimeout calls by target factory in source order (last-write-wins in Java)
                        Map<String, List<J.MethodInvocation>> timeoutsByFactory = new LinkedHashMap<>();
                        for (Statement statement : b.getStatements()) {
                            if (statement instanceof J.MethodInvocation) {
                                J.MethodInvocation invocation = (J.MethodInvocation) statement;
                                if (SET_CONNECT_TIMEOUT.matches(invocation) && invocation.getSelect() instanceof J.Identifier) {
                                    String factoryName = ((J.Identifier) invocation.getSelect()).getSimpleName();
                                    timeoutsByFactory.computeIfAbsent(factoryName, k -> new ArrayList<>()).add(invocation);
                                }
                            }
                        }

                        Set<String> reassigned = reassignedNames(b);

                        Map<String, Expression> migrations = new LinkedHashMap<>();
                        Set<UUID> removed = new HashSet<>();
                        for (Map.Entry<String, List<J.MethodInvocation>> entry : timeoutsByFactory.entrySet()) {
                            String factoryName = entry.getKey();
                            List<J.MethodInvocation> timeouts = entry.getValue();
                            String managerName = factoryToManager.get(factoryName);
                            if (managerName == null || !managerVariables.containsKey(managerName) ||
                                configuredManagers.contains(managerName) || reassigned.contains(managerName) ||
                                migrations.containsKey(managerName)) {
                                continue;
                            }
                            // Keeping an earlier call would override the connection config we are about to add
                            if (timeouts.size() > 1 && !safeToDropEarlier(b, timeouts, factoryName, managerName, clientToManager)) {
                                continue;
                            }
                            Expression timeout = timeouts.get(timeouts.size() - 1).getArguments().get(0);
                            if (!canEvaluateAfter(timeout, b, managerDeclarations.get(managerName))) {
                                continue;
                            }
                            migrations.put(managerName, timeout);
                            for (J.MethodInvocation invocation : timeouts) {
                                removed.add(invocation.getId());
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
                        for (Map.Entry<String, Expression> migration : migrations.entrySet()) {
                            String managerName = migration.getKey();
                            J.VariableDeclarations declaration = managerDeclarations.get(managerName);
                            J.VariableDeclarations.NamedVariable variable = managerVariables.get(managerName);
                            b = template.apply(new Cursor(getCursor().getParentOrThrow(), b), declaration.getCoordinates().after(),
                                    variable.getName().withPrefix(Space.EMPTY), migration.getValue());
                        }
                        return b.withStatements(ListUtils.map(b.getStatements(),
                                statement -> removed.contains(statement.getId()) ? null : statement));
                    }

                    private Set<String> reassignedNames(J.Block block) {
                        Set<String> names = new HashSet<>();
                        new JavaIsoVisitor<Set<String>>() {
                            @Override
                            public J.Assignment visitAssignment(J.Assignment assignment, Set<String> reassigned) {
                                if (assignment.getVariable() instanceof J.Identifier) {
                                    reassigned.add(((J.Identifier) assignment.getVariable()).getSimpleName());
                                }
                                return super.visitAssignment(assignment, reassigned);
                            }
                        }.reduce(block, names);
                        return names;
                    }

                    /**
                     * The timeout argument is relocated to just after the connection manager declaration, so it may only
                     * reference names that are already in scope there, and must be free of side effects.
                     */
                    private boolean canEvaluateAfter(Expression timeout, J.Block block, J.VariableDeclarations managerDeclaration) {
                        Set<String> declaredLater = new HashSet<>();
                        boolean atOrAfter = false;
                        for (Statement statement : block.getStatements()) {
                            atOrAfter |= statement.getId().equals(managerDeclaration.getId());
                            if (atOrAfter && statement instanceof J.VariableDeclarations) {
                                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                                    declaredLater.add(variable.getName().getSimpleName());
                                }
                            }
                        }
                        return !new JavaIsoVisitor<AtomicBoolean>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean unsafe) {
                                if (declaredLater.contains(identifier.getSimpleName())) {
                                    unsafe.set(true);
                                }
                                return super.visitIdentifier(identifier, unsafe);
                            }

                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, AtomicBoolean unsafe) {
                                unsafe.set(true);
                                return super.visitMethodInvocation(invocation, unsafe);
                            }

                            @Override
                            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean unsafe) {
                                unsafe.set(true);
                                return super.visitNewClass(newClass, unsafe);
                            }

                            @Override
                            public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean unsafe) {
                                unsafe.set(true);
                                return super.visitAssignment(assignment, unsafe);
                            }

                            @Override
                            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignment, AtomicBoolean unsafe) {
                                unsafe.set(true);
                                return super.visitAssignmentOperation(assignment, unsafe);
                            }

                            @Override
                            public J.Unary visitUnary(J.Unary unary, AtomicBoolean unsafe) {
                                if (unary.getOperator() != J.Unary.Type.Positive && unary.getOperator() != J.Unary.Type.Negative &&
                                    unary.getOperator() != J.Unary.Type.Complement && unary.getOperator() != J.Unary.Type.Not) {
                                    unsafe.set(true);
                                }
                                return super.visitUnary(unary, unsafe);
                            }
                        }.reduce(timeout, new AtomicBoolean()).get();
                    }

                    private boolean safeToDropEarlier(J.Block block, List<J.MethodInvocation> timeouts,
                                                     String factoryName, String managerName,
                                                     Map<String, String> clientToManager) {
                        Set<String> observers = new HashSet<>();
                        observers.add(factoryName);
                        observers.add(managerName);
                        for (Map.Entry<String, String> e : clientToManager.entrySet()) {
                            if (managerName.equals(e.getValue())) {
                                observers.add(e.getKey());
                            }
                        }
                        UUID firstId = timeouts.get(0).getId();
                        UUID lastId = timeouts.get(timeouts.size() - 1).getId();
                        Set<UUID> timeoutIds = new HashSet<>();
                        for (J.MethodInvocation t : timeouts) {
                            timeoutIds.add(t.getId());
                        }
                        boolean inRange = false;
                        for (Statement statement : block.getStatements()) {
                            if (statement.getId().equals(firstId)) {
                                inRange = true;
                                continue;
                            }
                            if (statement.getId().equals(lastId)) {
                                break;
                            }
                            if (!inRange || timeoutIds.contains(statement.getId())) {
                                continue;
                            }
                            if (referencesAny(statement, observers)) {
                                return false;
                            }
                        }
                        return true;
                    }

                    private boolean referencesAny(J tree, Set<String> names) {
                        return new JavaIsoVisitor<AtomicBoolean>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                                if (names.contains(identifier.getSimpleName())) {
                                    found.set(true);
                                }
                                return super.visitIdentifier(identifier, found);
                            }
                        }.reduce(tree, new AtomicBoolean()).get();
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

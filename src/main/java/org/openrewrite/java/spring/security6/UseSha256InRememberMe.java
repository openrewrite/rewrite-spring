/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.security6;

import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class UseSha256InRememberMe extends Recipe {

    private static final JavaType.Class REMEMBER_ME_TOKEN_ALGORITHM_TYPE = (JavaType.Class) JavaType.buildType("org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices$RememberMeTokenAlgorithm");
    private static final MethodMatcher REMEMBER_ME_SERVICES_CONSTRUCTOR_MATCHER =
            new MethodMatcher("org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices <constructor>(String, org.springframework.security.core.userdetails.UserDetailsService, "
                              + REMEMBER_ME_TOKEN_ALGORITHM_TYPE.getFullyQualifiedName() + ")");
    private static final MethodMatcher REMEMBER_ME_SERVICES_SET_MATCHING_ALGORITHM_MATCHER =
            new MethodMatcher("org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices setMatchingAlgorithm("
                              + REMEMBER_ME_TOKEN_ALGORITHM_TYPE.getFullyQualifiedName() + ")");

    @Override
    public String getDisplayName() {
        return "Remove explicit configuration of SHA-256 as encoding and matching algorithm for `TokenBasedRememberMeServices`";
    }

    @Override
    public String getDescription() {
        return "As of Spring Security 6.0 the SHA-256 algorithm is the default for the encoding and matching algorithm used by `TokenBasedRememberMeServices` and does thus no longer need to be explicitly configured."
               + "See the corresponding [https://docs.spring.io/spring-security/reference/6.0.0/migration/servlet/authentication.html#servlet-opt-in-sha256-rememberme](Sprint Security 6.0 migration step) for details.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                newClass = super.visitNewClass(newClass, ctx);
                if (newClass.getArguments().size() == 3 && REMEMBER_ME_SERVICES_CONSTRUCTOR_MATCHER.matches(newClass)) {
                    if (isSha256Algorithm(newClass.getArguments().get(2), getCursor())) {
                        return newClass.withArguments(new ArrayList<>(newClass.getArguments().subList(0, 2)));
                    }
                }
                return newClass;
            }

            @Override
            @SuppressWarnings("DataFlowIssue")
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);
                if (method.getSelect() != null && method.getArguments().size() == 1
                    && REMEMBER_ME_SERVICES_SET_MATCHING_ALGORITHM_MATCHER.matches(method)) {
                    if (isSha256Algorithm(method.getArguments().get(0), getCursor())) {
                        return null;
                    }
                }
                return method;
            }
        };
    }

    private boolean isSha256Algorithm(Expression expression, Cursor cursor) {
        JavaType.Variable fieldType = null;
        if (expression instanceof J.Identifier) {
            fieldType = ((J.Identifier) expression).getFieldType();
        } else if (expression instanceof J.FieldAccess) {
            fieldType = ((J.FieldAccess) expression).getName().getFieldType();
        }
        if (fieldType == null) {
            return false;
        }
        if (!TypeUtils.isOfType(fieldType.getOwner(), REMEMBER_ME_TOKEN_ALGORITHM_TYPE)) {
            Expression resolvedVariable = resolveVariable(fieldType.getName(), cursor);
            return resolvedVariable != null && resolvedVariable != expression && isSha256Algorithm(resolvedVariable, cursor);
        }
        return "SHA256".equals(fieldType.getName()) && TypeUtils.isOfType(fieldType.getType(), REMEMBER_ME_TOKEN_ALGORITHM_TYPE);
    }

    /**
     * Resolves a variable reference (by name) to the initializer expression of the corresponding declaration, provided that the
     * variable is declared as `final`. In all other cases `null` will be returned.
     */
    @Nullable
    private Expression resolveVariable(String name, Cursor cursor) {
        return resolveVariable0(name, cursor.getValue(), cursor.getParentTreeCursor());
    }

    @Nullable
    private Expression resolveVariable0(String name, J prior, Cursor cursor) {
        Optional<VariableMatch> found = Optional.empty();
        J value = cursor.getValue();
        if (value instanceof SourceFile) {
            return null;
        } else if (value instanceof J.MethodDeclaration) {
            found = findVariable(((J.MethodDeclaration) value).getParameters(), name);
        } else if (value instanceof J.Block) {
            J.Block block = (J.Block) value;
            List<Statement> statements = block.getStatements();
            boolean checkAllStatements = cursor.getParentTreeCursor().getValue() instanceof J.ClassDeclaration;
            if (!checkAllStatements) {
                int index = statements.indexOf(prior);
                statements = index != -1 ? statements.subList(0, index) : statements;
            }
            found = findVariable(statements, name);
        } else if (value instanceof J.ForLoop) {
            found = findVariable(((J.ForLoop) value).getControl().getInit(), name);
        } else if (value instanceof J.Try && ((J.Try) value).getResources() != null) {
            found = findVariable(((J.Try) value).getResources().stream().map(J.Try.Resource::getVariableDeclarations).collect(Collectors.toList()), name);
        } else if (value instanceof J.Lambda) {
            found = findVariable(((J.Lambda) value).getParameters().getParameters(), name);
        } else if (value instanceof J.VariableDeclarations) {
            found = findVariable(Collections.singletonList(((J.VariableDeclarations) value)), name);
        }
        return found.map(f -> f.isFinal ? f.variable.getInitializer() : null).orElseGet(() -> resolveVariable0(name, value, cursor.getParentTreeCursor()));
    }

    Optional<VariableMatch> findVariable(List<? extends J> list, String name) {
        for (J j : list) {
            if (j instanceof J.VariableDeclarations) {
                J.VariableDeclarations declaration = (J.VariableDeclarations) j;
                for (J.VariableDeclarations.NamedVariable variable : declaration.getVariables()) {
                    if (variable.getSimpleName().equals(name)) {
                        return Optional.of(new VariableMatch(variable, declaration.hasModifier(J.Modifier.Type.Final)));
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Value
    private static class VariableMatch {
        J.VariableDeclarations.NamedVariable variable;
        boolean isFinal;
    }
}

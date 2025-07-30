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
package org.openrewrite.java.spring.internal;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

// TODO Add in some form to the `rewrite-java` module
public class LocalVariableUtils {

    public static Expression resolveExpression(Expression expression, Cursor cursor) {
        JavaType.Variable fieldType = null;
        if (expression instanceof J.Identifier) {
            fieldType = ((J.Identifier) expression).getFieldType();
        } else if (expression instanceof J.FieldAccess) {
            fieldType = ((J.FieldAccess) expression).getName().getFieldType();
        }
        if (fieldType == null) {
            return expression;
        }
        JavaType owner = getRootOwner(fieldType);
        JavaType localRootType = getRootOwner(cursor);
        if (Objects.equals(owner, localRootType)) {
            Expression resolvedVariable = resolveVariable(fieldType.getName(), cursor);
            return resolvedVariable != null ? resolvedVariable : expression;
        }
        return expression;
    }

    private static @Nullable JavaType getRootOwner(Cursor cursor) {
        Cursor parent = cursor.dropParentUntil(is -> is instanceof J.MethodDeclaration || is instanceof J.ClassDeclaration || is instanceof SourceFile);
        Object parentValue = parent.getValue();
        if (parentValue instanceof SourceFile) {
            return null;
        }
        if (parentValue instanceof J.MethodDeclaration) {
            return getRootOwner(((J.MethodDeclaration) parentValue).getMethodType());
        }
        return getRootOwner(((J.ClassDeclaration) parentValue).getType());
    }

    private static JavaType getRootOwner(JavaType type) {
        if (type instanceof JavaType.Variable) {
            return getRootOwner(((JavaType.Variable) type).getOwner());
        }
        if (type instanceof JavaType.Method) {
            return getRootOwner(((JavaType.Method) type).getDeclaringType());
        }
        if (type instanceof JavaType.FullyQualified) {
            JavaType.FullyQualified owner = ((JavaType.FullyQualified) type).getOwningClass();
            return owner != null ? getRootOwner(owner) : type;
        }
        return type;
    }

    /**
     * Resolves a variable reference (by name) to the initializer expression of the corresponding declaration, provided that the
     * variable is declared as `final`. In all other cases `null` will be returned.
     */
    private static @Nullable Expression resolveVariable(String name, Cursor cursor) {
        return resolveVariable0(name, cursor.getValue(), cursor.getParentTreeCursor());
    }

    private static @Nullable Expression resolveVariable0(String name, J prior, Cursor cursor) {
        Optional<VariableMatch> found = Optional.empty();
        J value = cursor.getValue();
        if (value instanceof SourceFile) {
            return null;
        }
        if (value instanceof J.MethodDeclaration) {
            found = findVariable(((J.MethodDeclaration) value).getParameters(), name);
        } else if (value instanceof J.Block) {
            J.Block block = (J.Block) value;
            List<Statement> statements = block.getStatements();
            boolean checkAllStatements = cursor.getParentTreeCursor().getValue() instanceof J.ClassDeclaration;
            if (!checkAllStatements) {
                @SuppressWarnings("SuspiciousMethodCalls") int index = statements.indexOf(prior);
                statements = index != -1 ? statements.subList(0, index) : statements;
            }
            found = findVariable(statements, name);
        } else if (value instanceof J.ForLoop) {
            found = findVariable(((J.ForLoop) value).getControl().getInit(), name);
        } else if (value instanceof J.Try && ((J.Try) value).getResources() != null) {
            found = findVariable(((J.Try) value).getResources().stream().map(J.Try.Resource::getVariableDeclarations).collect(toList()), name);
        } else if (value instanceof J.Lambda) {
            found = findVariable(((J.Lambda) value).getParameters().getParameters(), name);
        } else if (value instanceof J.VariableDeclarations) {
            found = findVariable(singletonList(((J.VariableDeclarations) value)), name);
        }
        return found.map(f -> f.isFinal ? f.variable.getInitializer() : null).orElseGet(() -> resolveVariable0(name, value, cursor.getParentTreeCursor()));
    }

    private static Optional<VariableMatch> findVariable(List<? extends J> list, String name) {
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

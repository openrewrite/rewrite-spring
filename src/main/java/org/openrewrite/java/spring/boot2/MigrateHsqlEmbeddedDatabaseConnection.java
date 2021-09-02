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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MigrateHsqlEmbeddedDatabaseConnection extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deprecated Spring-Boot EmbeddedDatabaseConnection.HSQL";
    }

    @Override
    public String getDescription() {
        return "Spring-Boot `EmbeddedDatabaseConnection.HSQL` was deprecated in favor of `EmbeddedDatabaseConnection.HSQLDB` in 2.4.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.boot.jdbc.EmbeddedDatabaseConnection");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MigrateHsqlEmbeddedDatabaseConnection.UpdateDeprecatedConstantFieldNames();
    }

    private static class UpdateDeprecatedConstantFieldNames extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType.FullyQualified EMBEDDED_DATABASE_CONNECTION_FNQ =
                JavaType.Class.build("org.springframework.boot.jdbc.EmbeddedDatabaseConnection");
        private final Map<String, String> updateDeprecatedFields = new HashMap<>();

        UpdateDeprecatedConstantFieldNames() {
            updateDeprecatedFields.put("HSQL", "HSQLDB");
        }

        @Override
        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
            J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
            if (TypeUtils.isOfType(EMBEDDED_DATABASE_CONNECTION_FNQ, fa.getTarget().getType()) &&
                    updateDeprecatedFields.containsKey(fa.getName().getSimpleName())) {

                if (fa.getTarget() instanceof J.FieldAccess) {
                    fa = TypeTree.build(EMBEDDED_DATABASE_CONNECTION_FNQ.getFullyQualifiedName() + "." + updateDeprecatedFields.get(fieldAccess.getName().getSimpleName()))
                            .withPrefix(fa.getPrefix());
                } else {
                    fa = fa.withName(fa.getName().withName(updateDeprecatedFields.get(fa.getName().getSimpleName())));
                    fa = fa.withTarget(J.Identifier.build(
                            Tree.randomId(),
                            fa.getTarget().getPrefix(),
                            fa.getTarget().getMarkers(),
                            EMBEDDED_DATABASE_CONNECTION_FNQ.getClassName(),
                            EMBEDDED_DATABASE_CONNECTION_FNQ));
                }
            }
            return fa;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            J.Identifier id = super.visitIdentifier(identifier, ctx);
            if (isTargetFieldType(id) && updateDeprecatedFields.containsKey(id.getSimpleName())) {
                JavaType.Variable fieldType = ((JavaType.Variable) id.getFieldType());
                id = J.Identifier.build(
                        Tree.randomId(),
                        id.getPrefix(),
                        id.getMarkers(),
                        updateDeprecatedFields.get(id.getSimpleName()),
                        id.getType(),
                        JavaType.Variable.build(
                                updateDeprecatedFields.get(id.getSimpleName()),
                                EMBEDDED_DATABASE_CONNECTION_FNQ,
                                null,
                                Collections.emptyList(),
                                fieldType == null ? 0 : Flag.flagsToBitMap(fieldType.getFlags())));
            }
            return id;
        }

        private boolean isTargetFieldType(J.Identifier identifier) {
            if (identifier.getFieldType() != null && identifier.getFieldType() instanceof JavaType.Variable) {
                JavaType.FullyQualified fqn = TypeUtils.asFullyQualified(((JavaType.Variable) identifier.getFieldType()).getOwner());
                return fqn != null && EMBEDDED_DATABASE_CONNECTION_FNQ.getFullyQualifiedName().equals(fqn.getFullyQualifiedName());
            }
            return false;
        }
    }
}

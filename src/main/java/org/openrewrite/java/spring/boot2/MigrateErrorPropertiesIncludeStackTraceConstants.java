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

import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MigrateErrorPropertiesIncludeStackTraceConstants extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use `ErrorProperties#IncludeStacktrace.ON_PARAM`";
    }

    @Override
    public String getDescription() {
        return "`ErrorProperties#IncludeStacktrace.ON_TRACE_PARAM` was deprecated in 2.3.x and removed in 2.5.0.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.boot.autoconfigure.web.ErrorProperties$IncludeStacktrace");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MigrateErrorPropertiesIncludeStackTraceConstants.UpdateDeprecatedConstantFieldNames();
    }

    private static class UpdateDeprecatedConstantFieldNames extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType.FullyQualified ORIGINAL_FQN =
                JavaType.ShallowClass.build("org.springframework.boot.autoconfigure.web.ErrorProperties$IncludeStacktrace");

        private final Map<String, String> updateDeprecatedFields = new HashMap<>();

        UpdateDeprecatedConstantFieldNames() {
            updateDeprecatedFields.put("ON_TRACE_PARAM", "ON_PARAM");
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            if (!classDecl.getSimpleName().equals(ORIGINAL_FQN.getClassName())) {
                maybeRemoveImport(ORIGINAL_FQN);
            }

            return cd;
        }

        @Override
        public J.Import visitImport(J.Import anImport, ExecutionContext executionContext) {
            J.Identifier name = anImport.getQualid().getName();
            if (anImport.isStatic() && updateDeprecatedFields.containsKey(name.getSimpleName()) &&
                    TypeUtils.isOfClassType(anImport.getQualid().getTarget().getType(), ORIGINAL_FQN.getFullyQualifiedName())) {
                return anImport.withQualid(anImport.getQualid().withName(name.withSimpleName(updateDeprecatedFields.get(name.getSimpleName()))));
            }
            return super.visitImport(anImport, executionContext);
        }

        @Override
        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
            J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
            if (isTargetClass() &&
                    TypeUtils.isOfType(ORIGINAL_FQN, fa.getTarget().getType()) &&
                    updateDeprecatedFields.containsKey(fa.getName().getSimpleName())) {

                fa = fa.withName(fa.getName().withSimpleName(updateDeprecatedFields.get(fa.getName().getSimpleName())));
                String className;
                if (fa.getTarget() instanceof J.Identifier) {
                    className = ORIGINAL_FQN.getClassName().substring(ORIGINAL_FQN.getClassName().lastIndexOf(".") + 1);
                } else {
                    className = ORIGINAL_FQN.getClassName();
                }

                fa = fa.withTarget(new J.Identifier(
                        Tree.randomId(),
                        fa.getTarget().getPrefix(),
                        fa.getTarget().getMarkers(),
                        className,
                        ORIGINAL_FQN,
                        null));
            }
            return fa;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            J.Identifier id = super.visitIdentifier(identifier, ctx);
            if (isTargetClass() && isTargetFieldType(id) && updateDeprecatedFields.containsKey(id.getSimpleName())) {
                JavaType.Variable fieldType = id.getFieldType();
                id = new J.Identifier(
                        Tree.randomId(),
                        id.getPrefix(),
                        id.getMarkers(),
                        updateDeprecatedFields.get(id.getSimpleName()),
                        id.getType(),
                        fieldType == null ? null : new JavaType.Variable(
                                null,
                                Flag.flagsToBitMap(fieldType.getFlags()),
                                updateDeprecatedFields.get(id.getSimpleName()),
                                ORIGINAL_FQN,
                                ORIGINAL_FQN,
                                Collections.emptyList()));
            }
            return id;
        }

        private boolean isTargetClass() {
            Cursor parentCursor = getCursor().dropParentUntil(
                    is -> is instanceof J.CompilationUnit ||
                            is instanceof J.ClassDeclaration);
            return parentCursor.getValue() instanceof J.ClassDeclaration &&
                    !((J.ClassDeclaration) parentCursor.getValue()).getName().getSimpleName().equals(ORIGINAL_FQN.getClassName());
        }

        private boolean isTargetFieldType(J.Identifier identifier) {
            if (identifier.getFieldType() != null) {
                JavaType.FullyQualified fqn = TypeUtils.asFullyQualified(identifier.getFieldType().getOwner());
                return fqn != null && ORIGINAL_FQN.getFullyQualifiedName().equals(fqn.getFullyQualifiedName());
            }
            return false;
        }
    }

}

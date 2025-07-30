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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.*;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;

public class MigrateLoggingSystemPropertyConstants extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate to recommended constants in `LogbackLoggingSystemProperties` from deprecated values in `LoggingSystemProperties`";
    }

    @Override
    public String getDescription() {
        return "Replaces field and static access of deprecated fields in `LoggingSystemProperties` with the recommendations from `LogbackLoggingSystemProperties`. Deprecated in 2.4.x and removed in 2.6.0.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.boot.logging.LoggingSystemProperties", false),
                new MigrateLoggingSystemPropertyConstants.UpdateDeprecatedConstantFieldNames());
    }

    private static class UpdateDeprecatedConstantFieldNames extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType.FullyQualified ORIGINAL_FQN =
                JavaType.ShallowClass.build("org.springframework.boot.logging.LoggingSystemProperties");
        private static final JavaType.FullyQualified NEW_FQN =
                JavaType.ShallowClass.build("org.springframework.boot.logging.logback.LogbackLoggingSystemProperties");

        private final Map<String, String> updateDeprecatedFields = new HashMap<>();

        UpdateDeprecatedConstantFieldNames() {
            updateDeprecatedFields.put("FILE_CLEAN_HISTORY_ON_START", "ROLLINGPOLICY_CLEAN_HISTORY_ON_START");
            updateDeprecatedFields.put("FILE_MAX_HISTORY", "ROLLINGPOLICY_MAX_HISTORY");
            updateDeprecatedFields.put("FILE_MAX_SIZE", "ROLLINGPOLICY_MAX_FILE_SIZE");
            updateDeprecatedFields.put("FILE_TOTAL_SIZE_CAP", "ROLLINGPOLICY_TOTAL_SIZE_CAP");
            updateDeprecatedFields.put("ROLLING_FILE_NAME_PATTERN", "ROLLINGPOLICY_FILE_NAME_PATTERN");
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            if (!classDecl.getSimpleName().equals(ORIGINAL_FQN.getClassName())) {
                maybeRemoveImport(ORIGINAL_FQN);
                maybeAddImport(NEW_FQN);
            }

            return cd;
        }

        @Override
        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
            J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
            if (TypeUtils.isOfType(ORIGINAL_FQN, fa.getTarget().getType()) &&
                    updateDeprecatedFields.containsValue(fa.getName().getSimpleName())) {

                if (fa.getTarget() instanceof J.FieldAccess) {
                    fa = TypeTree.build(NEW_FQN.getFullyQualifiedName() + "." + fa.getName().getSimpleName())
                            .withPrefix(fa.getPrefix());
                } else {
                    fa = fa.withName(fa.getName().withSimpleName(fa.getName().getSimpleName()));
                    fa = fa.withTarget(new J.Identifier(
                            Tree.randomId(),
                            fa.getTarget().getPrefix(),
                            fa.getTarget().getMarkers(),
                            emptyList(),
                            NEW_FQN.getClassName(),
                            NEW_FQN,
                            null));
                }
            }
            return fa;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            J.Identifier id = super.visitIdentifier(identifier, ctx);
            if (isTargetFieldType(id) && updateDeprecatedFields.containsKey(id.getSimpleName())) {
                JavaType.Variable fieldType = id.getFieldType();
                id = new J.Identifier(
                        Tree.randomId(),
                        id.getPrefix(),
                        id.getMarkers(),
                        emptyList(),
                        updateDeprecatedFields.get(id.getSimpleName()),
                        id.getType(),
                        new JavaType.Variable(
                                null,
                                fieldType == null ? 0 : Flag.flagsToBitMap(fieldType.getFlags()),
                                updateDeprecatedFields.get(id.getSimpleName()),
                                NEW_FQN,
                                id.getType(),
                                emptyList()));

                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu != null) {
                    for (J.Import anImport : cu.getImports()) {
                        if (anImport.isStatic() && TypeUtils.isOfType(anImport.getQualid().getTarget().getType(), ORIGINAL_FQN) &&
                                (updateDeprecatedFields.containsKey(anImport.getQualid().getName().getSimpleName()) ||
                                        "*".equals(anImport.getQualid().getName().getSimpleName()))) {
                            maybeRemoveImport(ORIGINAL_FQN);
                            doAfterVisit(new AddImport<>(NEW_FQN.getFullyQualifiedName(), id.getSimpleName(), false));
                        }
                    }
                }
            }
            return id;
        }

        private boolean isTargetFieldType(J.Identifier identifier) {
            if (identifier.getFieldType() != null) {
                JavaType.FullyQualified fqn = TypeUtils.asFullyQualified((identifier.getFieldType()).getOwner());
                return fqn != null && ORIGINAL_FQN.getFullyQualifiedName().equals(fqn.getFullyQualifiedName());
            }
            return false;
        }
    }
}

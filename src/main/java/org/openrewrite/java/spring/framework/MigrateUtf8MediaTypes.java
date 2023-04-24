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
package org.openrewrite.java.spring.framework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MigrateUtf8MediaTypes extends Recipe {
    private final JavaType.FullyQualified mediaTypeFqn =
            JavaType.ShallowClass.build("org.springframework.http.MediaType");

    @Override
    public String getDisplayName() {
        return "Migrate deprecated Spring Web UTF8 `MediaType` enums";
    }

    @Override
    public String getDescription() {
        return "Spring Web `MediaType#APPLICATION_JSON_UTF8` and `MediaType#APPLICATION_PROBLEM_JSON_UTF8` were deprecated in 5.2.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.http.MediaType", false);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final Map<String, String> updateDeprecatedFields = new HashMap<String, String>() {{
                put("APPLICATION_JSON_UTF8", "APPLICATION_JSON");
                put("APPLICATION_JSON_UTF8_VALUE", "APPLICATION_JSON_VALUE");
                put("APPLICATION_PROBLEM_JSON_UTF8", "APPLICATION_PROBLEM_JSON");
                put("APPLICATION_PROBLEM_JSON_UTF8_VALUE", "APPLICATION_PROBLEM_JSON_VALUE");
            }};

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                if (TypeUtils.isOfType(mediaTypeFqn, fa.getTarget().getType()) &&
                    updateDeprecatedFields.containsKey(fa.getName().getSimpleName())) {

                    if (fa.getTarget() instanceof J.FieldAccess) {
                        fa = TypeTree.build(mediaTypeFqn.getFullyQualifiedName() + "." + updateDeprecatedFields.get(fieldAccess.getName().getSimpleName()))
                                .withPrefix(fa.getPrefix());
                    } else {
                        fa = fa.withName(fa.getName().withSimpleName(updateDeprecatedFields.get(fa.getName().getSimpleName())));
                        fa = fa.withTarget(new J.Identifier(
                                Tree.randomId(),
                                fa.getTarget().getPrefix(),
                                fa.getTarget().getMarkers(),
                                mediaTypeFqn.getClassName(),
                                mediaTypeFqn,
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
                            updateDeprecatedFields.get(id.getSimpleName()),
                            id.getType(),
                            new JavaType.Variable(
                                    null,
                                    fieldType == null ? 0 : Flag.flagsToBitMap(fieldType.getFlags()),
                                    updateDeprecatedFields.get(id.getSimpleName()),
                                    mediaTypeFqn,
                                    null,
                                    Collections.emptyList()));
                }
                return id;
            }

            private boolean isTargetFieldType(J.Identifier identifier) {
                if (identifier.getFieldType() != null) {
                    JavaType.FullyQualified fqn = TypeUtils.asFullyQualified((identifier.getFieldType()).getOwner());
                    return fqn != null && mediaTypeFqn.getFullyQualifiedName().equals(fqn.getFullyQualifiedName());
                }
                return false;
            }
        };
    }
}

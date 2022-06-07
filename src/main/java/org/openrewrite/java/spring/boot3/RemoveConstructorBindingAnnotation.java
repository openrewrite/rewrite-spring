/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

/**
 * @author Alex Boyko
 */
public class RemoveConstructorBindingAnnotation extends Recipe {

    private static final String ANNOTATION_CONSTRUCTOR_BINDING = "org.springframework.boot.context.properties.ConstructorBinding";
    private static final String ANNOTATION_CONFIG_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";

    @Override
    public String getDisplayName() {
        return "Remove Unnecessary @ConstructorBinding";
    }

    @Override
    public String getDescription() {
        return "As of Boot 3.0 @ConstructorBinding is no longer needed at the type level on @ConfigurationProperties classes and should be removed.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext context) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, context);
                if (classDecl.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), ANNOTATION_CONFIG_PROPERTIES))) {
                    c = c.withLeadingAnnotations(ListUtils.map(c.getLeadingAnnotations(), anno -> {
                        if (TypeUtils.isOfClassType(anno.getType(), ANNOTATION_CONSTRUCTOR_BINDING)) {
                            maybeRemoveImport(ANNOTATION_CONSTRUCTOR_BINDING);
                            return null;
                        }
                        return anno;
                    }));
                }
                return c;
            }
        };
    }

}

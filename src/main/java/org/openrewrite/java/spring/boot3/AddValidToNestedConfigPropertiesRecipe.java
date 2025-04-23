/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;

public class AddValidToNestedConfigPropertiesRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add `@Valid` to nested properties in `@ConfigurationProperties`";
    }

    @Override
    public String getDescription() {
        return "Adds `@Valid` annotation to fields in `@ConfigurationProperties` classes that contain nested properties with validation constraints.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            boolean visitedTopLevelClass = false;

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (visitedTopLevelClass) {
                    return super.visitClassDeclaration(classDecl, ctx);
                }
                visitedTopLevelClass = true;
                if (!FindAnnotations.find(classDecl, "@org.springframework.boot.context.properties.ConfigurationProperties").isEmpty() &&
                    !FindAnnotations.find(classDecl, "@org.springframework.validation.annotation.Validated").isEmpty()) {
                    return super.visitClassDeclaration(classDecl, ctx);
                }
                return classDecl;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations varDecl, ExecutionContext ctx) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(varDecl.getType());
                if (type != null && !isPrimitiveOrCommonType(type)) {
                    if (FindAnnotations.find(varDecl, "@javax.validation.Valid").isEmpty() &&
                        FindAnnotations.find(varDecl, "@jakarta.validation.Valid").isEmpty()) {
                        maybeAddImport("jakarta.validation.Valid");
                        JavaTemplate template = JavaTemplate.builder("@Valid")
                            .imports("jakarta.validation.Valid")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.validation-api"))
                            .build();
                        return template.apply(getCursor(), varDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    }
                }
                return super.visitVariableDeclarations(varDecl, ctx);
            }

            private boolean isPrimitiveOrCommonType(JavaType.FullyQualified type) {
                String name = type.getFullyQualifiedName();
                return name.startsWith("java.") ||
                    name.startsWith("javax.") ||
                    name.startsWith("sun.") ||
                    name.startsWith("com.sun.") ||
                    name.startsWith("jdk.");
            }
        };
    }
}

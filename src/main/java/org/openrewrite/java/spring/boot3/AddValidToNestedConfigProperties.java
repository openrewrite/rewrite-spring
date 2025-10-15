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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;

public class AddValidToNestedConfigProperties extends Recipe {

    private static final String CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";
    private static final String VALIDATED = "org.springframework.validation.annotation.Validated";
    private static final String JAKARTA_VALIDATION_VALID = "jakarta.validation.Valid";

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
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(CONFIGURATION_PROPERTIES, true),
                        new UsesType<>(VALIDATED, true)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    boolean visitedTopLevelClass;

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        if (visitedTopLevelClass) {
                            return super.visitClassDeclaration(classDecl, ctx);
                        }
                        visitedTopLevelClass = true;
                        if (!FindAnnotations.find(classDecl, "@" + CONFIGURATION_PROPERTIES).isEmpty() &&
                                !FindAnnotations.find(classDecl, "@" + VALIDATED).isEmpty()) {
                            return super.visitClassDeclaration(classDecl, ctx);
                        }
                        return classDecl;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations varDecl, ExecutionContext ctx) {
                        JavaType.FullyQualified type = TypeUtils.asFullyQualified(varDecl.getType());
                        if (type != null && !isPrimitiveOrCommonType(type) &&
                                FindAnnotations.find(varDecl, "@javax.validation.Valid").isEmpty() &&
                                FindAnnotations.find(varDecl, "@" + JAKARTA_VALIDATION_VALID).isEmpty()) {
                            maybeAddImport(JAKARTA_VALIDATION_VALID);
                            return JavaTemplate.builder("@Valid")
                                    .imports(JAKARTA_VALIDATION_VALID)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.validation-api"))
                                    .build()
                                    .apply(getCursor(), varDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
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
                });
    }
}

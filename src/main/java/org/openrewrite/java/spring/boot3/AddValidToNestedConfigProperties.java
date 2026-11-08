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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;

public class AddValidToNestedConfigProperties extends Recipe {

    private static final String CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";
    private static final String VALIDATED = "org.springframework.validation.annotation.Validated";
    private static final String JAKARTA_VALIDATION_VALID = "jakarta.validation.Valid";
    private static final AnnotationMatcher CONFIGURATION_PROPERTIES_MATCHER = new AnnotationMatcher("@" + CONFIGURATION_PROPERTIES);
    private static final AnnotationMatcher VALIDATED_MATCHER = new AnnotationMatcher("@" + VALIDATED);

    @Getter
    final String displayName = "Add `@Valid` to nested properties in `@ConfigurationProperties`";

    @Getter
    final String description = "Adds `@Valid` annotation to fields in `@ConfigurationProperties` classes that contain nested properties with validation constraints.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(CONFIGURATION_PROPERTIES, true),
                        new UsesType<>(VALIDATED, true)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    boolean inPropertiesClass;

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        boolean enclosing = inPropertiesClass;
                        AnnotationService annotations = service(AnnotationService.class);
                        J.ClassDeclaration.Kind.Type kind = classDecl.getKind();
                        // A class or record nested in a properties class is a nested property type.
                        // An enum is bound from a constant's name, and the fields of an interface are constants.
                        inPropertiesClass = annotations.matches(getCursor(), CONFIGURATION_PROPERTIES_MATCHER) &&
                                annotations.matches(getCursor(), VALIDATED_MATCHER) ||
                                enclosing && (kind == J.ClassDeclaration.Kind.Type.Class || kind == J.ClassDeclaration.Kind.Type.Record);
                        J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                        inPropertiesClass = enclosing;
                        return c;
                    }

                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        if (getCursor().getParentTreeCursor().getValue() instanceof J.ClassDeclaration) {
                            return super.visitBlock(block, ctx);
                        }
                        // Any other block is code, whose declarations, local classes included, are not properties
                        return block;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        // Validation cascades through fields and record components, not method parameters
                        return method;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations varDecl, ExecutionContext ctx) {
                        if (!inPropertiesClass || varDecl.hasModifier(J.Modifier.Type.Static)) {
                            return varDecl;
                        }
                        JavaType.FullyQualified type = TypeUtils.asFullyQualified(varDecl.getType());
                        if (type != null && type.getKind() != JavaType.FullyQualified.Kind.Enum &&
                                !isPrimitiveOrCommonType(type) &&
                                FindAnnotations.find(varDecl, "@javax.validation.Valid").isEmpty() &&
                                FindAnnotations.find(varDecl, "@" + JAKARTA_VALIDATION_VALID).isEmpty()) {
                            maybeAddImport(JAKARTA_VALIDATION_VALID);
                            return JavaTemplate.builder("@Valid")
                                    .imports(JAKARTA_VALIDATION_VALID)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.validation-api"))
                                    .build()
                                    .apply(getCursor(), varDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        }
                        // An initializer is code
                        return varDecl;
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

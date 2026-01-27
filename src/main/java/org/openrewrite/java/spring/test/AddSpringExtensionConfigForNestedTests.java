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
package org.openrewrite.java.spring.test;

import lombok.Getter;
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

import java.util.Comparator;

public class AddSpringExtensionConfigForNestedTests extends Recipe {

    private static final String NESTED_ANNOTATION = "org.junit.jupiter.api.Nested";
    private static final String SPRING_EXTENSION = "org.springframework.test.context.junit.jupiter.SpringExtension";
    private static final String SPRING_EXTENSION_CONFIG = "org.springframework.test.context.junit.jupiter.SpringExtensionConfig";

    @Getter
    final String displayName = "Add `@SpringExtensionConfig` for nested tests";

    @Getter
    final String description = "Spring Framework 7.0 changed `SpringExtension` to use test-method scoped `ExtensionContext` " +
            "instead of test-class scoped. This can break `@Nested` test class hierarchies. " +
            "Adding `@SpringExtensionConfig(useTestClassScopedExtensionContext = true)` restores the previous behavior.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(SPRING_EXTENSION, true),
                        new UsesType<>(NESTED_ANNOTATION, false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        // Only process top-level classes (not inner classes)
                        if (getCursor().getParentTreeCursor().getValue() instanceof J.ClassDeclaration) {
                            return cd;
                        }

                        // Skip if already has @SpringExtensionConfig
                        if (!FindAnnotations.find(cd, "@" + SPRING_EXTENSION_CONFIG, true).isEmpty()) {
                            return cd;
                        }

                        // Check if this class uses SpringExtension (directly or via meta-annotation)
                        if (FindAnnotations.find(cd, "@org.junit.jupiter.api.extension.ExtendWith(" + SPRING_EXTENSION + ".class)", true).isEmpty()) {
                            return cd;
                        }

                        // Check if class has @Nested inner classes
                        boolean hasNestedClasses = cd.getBody().getStatements().stream()
                                .filter(J.ClassDeclaration.class::isInstance)
                                .map(J.ClassDeclaration.class::cast)
                                .anyMatch(innerClass -> !FindAnnotations.find(innerClass, "@" + NESTED_ANNOTATION).isEmpty());

                        if (!hasNestedClasses) {
                            return cd;
                        }

                        maybeAddImport(SPRING_EXTENSION_CONFIG);

                        return JavaTemplate.builder("@SpringExtensionConfig(useTestClassScopedExtensionContext = true)")
                                .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                        "package org.springframework.test.context.junit.jupiter;" +
                                        "import java.lang.annotation.*;" +
                                        "@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) @Documented @Inherited " +
                                        "public @interface SpringExtensionConfig { boolean useTestClassScopedExtensionContext(); }"))
                                .imports(SPRING_EXTENSION_CONFIG)
                                .build()
                                .apply(getCursor(), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    }
                }
        );
    }
}

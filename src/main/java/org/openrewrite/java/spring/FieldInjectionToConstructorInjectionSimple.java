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
package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class FieldInjectionToConstructorInjectionSimple extends Recipe {
    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
    private static final String QUALIFIER = "org.springframework.beans.factory.annotation.Qualifier";

    @Option(displayName = "Maximum autowired fields",
            description = "The maximum number of autowired fields to convert. Classes with more autowired fields than this will be skipped.",
            required = false)
    @Nullable
    Integer maxAutowiredFields;

    @Override
    public String getDisplayName() {
        return "Convert field injection to constructor injection";
    }

    @Override
    public String getDescription() {
        return "Converts Spring's `@Autowired` field injection to constructor injection.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(AUTOWIRED, false),
                new FieldInjectionToConstructorInjectionVisitor(maxAutowiredFields)
        );
    }

    private static class FieldInjectionToConstructorInjectionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final Integer maxAutowiredFields;

        public FieldInjectionToConstructorInjectionVisitor(@Nullable Integer maxAutowiredFields) {
            this.maxAutowiredFields = maxAutowiredFields;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            // Skip if not a class
            if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                return classDecl;
            }

            // Skip if class extends another class
            if (classDecl.getExtends() != null) {
                return classDecl;
            }

            // Find all autowired fields
            List<J.VariableDeclarations> autowiredFields = new ArrayList<>();
            for (Statement statement : classDecl.getBody().getStatements()) {
                if (statement instanceof J.VariableDeclarations) {
                    J.VariableDeclarations varDecl = (J.VariableDeclarations) statement;
                    if (hasAutowiredAnnotation(varDecl)) {
                        autowiredFields.add(varDecl);
                    }
                }
            }

            // Skip if no autowired fields
            if (autowiredFields.isEmpty()) {
                return classDecl;
            }

            // Skip if too many autowired fields
            if (maxAutowiredFields != null && autowiredFields.size() > maxAutowiredFields) {
                return classDecl;
            }

            // Find all constructors
            List<J.MethodDeclaration> constructors = classDecl.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast)
                    .filter(J.MethodDeclaration::isConstructor)
                    .collect(Collectors.toList());

            // Skip if has explicit constructor with parameters
            if (constructors.stream().anyMatch(c -> !c.getParameters().isEmpty())) {
                return classDecl;
            }

            // Skip if has more than one constructor
            if (constructors.size() > 1) {
                return classDecl;
            }

            // Process the class
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            // Check if any fields have @Qualifier annotations
            boolean hasQualifierAnnotations = autowiredFields.stream()
                    .anyMatch(field -> field.getLeadingAnnotations().stream()
                            .anyMatch(annotation -> TypeUtils.isOfClassType(annotation.getType(), QUALIFIER)));

            // Add import for @Qualifier if needed
            if (hasQualifierAnnotations) {
                maybeAddImport(QUALIFIER);
            }

            // Create a new class with constructor injection
            StringBuilder newClassTemplate = new StringBuilder();
            newClassTemplate.append("package ").append(cd.getType().getPackageName()).append(";\n\n");

            // Add imports
            if (hasQualifierAnnotations) {
                newClassTemplate.append("import ").append(QUALIFIER).append(";\n\n");
            }

            // Add class declaration
            newClassTemplate.append("public class ").append(cd.getSimpleName()).append(" {\n\n");

            // Add fields
            for (J.VariableDeclarations field : autowiredFields) {
                J.Identifier name = field.getVariables().get(0).getName();
                TypeTree typeExpr = field.getTypeExpression();

                // Add field declaration
                newClassTemplate.append("    private final ").append(typeExpr.printTrimmed()).append(" ").append(name.getSimpleName()).append(";\n");
            }

            newClassTemplate.append("\n");

            // Add constructor
            newClassTemplate.append("    public ").append(cd.getSimpleName()).append("(");

            // Add constructor parameters
            for (int i = 0; i < autowiredFields.size(); i++) {
                J.VariableDeclarations field = autowiredFields.get(i);
                J.Identifier name = field.getVariables().get(0).getName();
                TypeTree typeExpr = field.getTypeExpression();

                if (i > 0) {
                    newClassTemplate.append(", ");
                }

                // Add @Qualifier annotation if present
                J.Annotation qualifierAnnotation = getQualifierAnnotation(field);
                if (qualifierAnnotation != null) {
                    newClassTemplate.append(qualifierAnnotation.printTrimmed()).append(" ");
                }

                newClassTemplate.append(typeExpr.printTrimmed()).append(" ").append(name.getSimpleName());
            }

            newClassTemplate.append(") {\n");

            // Add constructor body
            for (J.VariableDeclarations field : autowiredFields) {
                J.Identifier name = field.getVariables().get(0).getName();
                newClassTemplate.append("        this.").append(name.getSimpleName()).append(" = ").append(name.getSimpleName()).append(";\n");
            }

            newClassTemplate.append("    }\n");

            // Add other methods and inner classes
            for (Statement statement : cd.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration) {
                    J.MethodDeclaration method = (J.MethodDeclaration) statement;
                    if (!method.isConstructor()) {
                        newClassTemplate.append("\n    ").append(method.printTrimmed().replace("\n", "\n    ")).append("\n");
                    }
                } else if (statement instanceof J.ClassDeclaration) {
                    J.ClassDeclaration innerClass = (J.ClassDeclaration) statement;
                    newClassTemplate.append("\n    ").append(innerClass.printTrimmed().replace("\n", "\n    ")).append("\n");
                }
            }

            newClassTemplate.append("}\n");

            // Replace the entire class
            try {
                J.CompilationUnit cu = (J.CompilationUnit) JavaParser.fromJavaVersion()
                        .build()
                        .parse(newClassTemplate.toString())
                        .findFirst()
                        .orElse(null);

                if (cu != null && !cu.getClasses().isEmpty()) {
                    return cu.getClasses().get(0);
                }
            } catch (Exception e) {
                // If parsing fails, return the original class
            }

            return cd; // Return original if parsing fails
        }

        private boolean hasAutowiredAnnotation(J.VariableDeclarations varDecl) {
            return varDecl.getLeadingAnnotations().stream()
                    .anyMatch(annotation -> TypeUtils.isOfClassType(annotation.getType(), AUTOWIRED));
        }

        @Nullable
        private J.Annotation getQualifierAnnotation(J.VariableDeclarations field) {
            return field.getLeadingAnnotations().stream()
                    .filter(annotation -> TypeUtils.isOfClassType(annotation.getType(), QUALIFIER))
                    .findFirst()
                    .orElse(null);
        }
    }
}

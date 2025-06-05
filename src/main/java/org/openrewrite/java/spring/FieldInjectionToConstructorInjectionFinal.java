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
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class FieldInjectionToConstructorInjectionFinal extends Recipe {
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

            // Remove import for @Autowired
            maybeRemoveImport(AUTOWIRED);

            // Create a new constructor
            StringBuilder constructorTemplate = new StringBuilder();
            constructorTemplate.append("public ").append(cd.getSimpleName()).append("(");

            // Add constructor parameters
            for (int i = 0; i < autowiredFields.size(); i++) {
                J.VariableDeclarations field = autowiredFields.get(i);
                J.Identifier name = field.getVariables().get(0).getName();
                TypeTree typeExpr = field.getTypeExpression();

                if (i > 0) {
                    constructorTemplate.append(", ");
                }

                // Add @Qualifier annotation if present
                J.Annotation qualifierAnnotation = getQualifierAnnotation(field);
                if (qualifierAnnotation != null) {
                    constructorTemplate.append(qualifierAnnotation.printTrimmed()).append(" ");
                }

                constructorTemplate.append(typeExpr.printTrimmed()).append(" ").append(name.getSimpleName());
            }

            constructorTemplate.append(") {\n");

            // Add constructor body
            for (J.VariableDeclarations field : autowiredFields) {
                J.Identifier name = field.getVariables().get(0).getName();
                constructorTemplate.append("    this.").append(name.getSimpleName()).append(" = ").append(name.getSimpleName()).append(";\n");
            }

            constructorTemplate.append("}");

            // Create the constructor template
            JavaTemplate constructorJavaTemplate = JavaTemplate.builder(constructorTemplate.toString())
                    .contextSensitive()
                    .imports(QUALIFIER)
                    .build();

            // Find the existing constructor or insertion point
            List<Statement> statements = new ArrayList<>(cd.getBody().getStatements());
            int constructorIndex = -1;
            for (int i = 0; i < statements.size(); i++) {
                Statement statement = statements.get(i);
                if (statement instanceof J.MethodDeclaration) {
                    J.MethodDeclaration method = (J.MethodDeclaration) statement;
                    if (method.isConstructor()) {
                        constructorIndex = i;
                        break;
                    }
                }
            }

            // Replace the existing constructor or add a new one
            if (constructorIndex >= 0) {
                Statement existingConstructor = statements.get(constructorIndex);
                statements.set(constructorIndex, constructorJavaTemplate.apply(
                    getCursor(),
                    existingConstructor.getCoordinates().replace()
                ));
            } else {
                // Find insertion point after fields
                int insertionPoint = 0;
                for (int i = 0; i < statements.size(); i++) {
                    Statement statement = statements.get(i);
                    if (statement instanceof J.VariableDeclarations) {
                        insertionPoint = i + 1;
                    }
                }
                statements.add(insertionPoint, constructorJavaTemplate.apply(
                    getCursor(),
                    cd.getBody().getCoordinates().firstStatement()
                ));
            }

            // Remove @Autowired annotations from fields and make them final
            for (int i = 0; i < statements.size(); i++) {
                Statement statement = statements.get(i);
                if (statement instanceof J.VariableDeclarations) {
                    J.VariableDeclarations varDecl = (J.VariableDeclarations) statement;
                    if (hasAutowiredAnnotation(varDecl)) {
                        // Remove @Autowired and @Qualifier annotations
                        List<J.Annotation> newAnnotations = new ArrayList<>();
                        for (J.Annotation annotation : varDecl.getLeadingAnnotations()) {
                            if (!TypeUtils.isOfClassType(annotation.getType(), AUTOWIRED) && 
                                !TypeUtils.isOfClassType(annotation.getType(), QUALIFIER)) {
                                newAnnotations.add(annotation);
                            }
                        }
                        
                        J.VariableDeclarations vd = varDecl.withLeadingAnnotations(newAnnotations);
                        
                        // Add final modifier if not present
                        if (vd.getModifiers().stream().noneMatch(m -> m.getType() == J.Modifier.Type.Final)) {
                            List<J.Modifier> modifiers = new ArrayList<>(vd.getModifiers());
                            // Find the private modifier to add final after it
                            int privateIndex = -1;
                            for (int j = 0; j < modifiers.size(); j++) {
                                if (modifiers.get(j).getType() == J.Modifier.Type.Private) {
                                    privateIndex = j;
                                    break;
                                }
                            }
                            J.Modifier finalModifier = new J.Modifier(
                                Tree.randomId(),
                                Space.format(" "),
                                Markers.EMPTY,
                                null,
                                J.Modifier.Type.Final,
                                Collections.emptyList()
                            );
                            
                            if (privateIndex >= 0) {
                                modifiers.add(privateIndex + 1, finalModifier);
                            } else {
                                modifiers.add(finalModifier);
                            }
                            
                            vd = vd.withModifiers(modifiers);
                        }
                        
                        // Remove initializer if present
                        if (!vd.getVariables().isEmpty() && vd.getVariables().get(0).getInitializer() != null) {
                            List<J.VariableDeclarations.NamedVariable> variables = new ArrayList<>();
                            for (J.VariableDeclarations.NamedVariable variable : vd.getVariables()) {
                                variables.add(variable.withInitializer(null));
                            }
                            vd = vd.withVariables(variables);
                        }
                        statements.set(i, vd);
                    }
                }
            }

            return cd.withBody(cd.getBody().withStatements(statements));
        }

        private boolean hasAutowiredAnnotation(J.VariableDeclarations varDecl) {
            return varDecl.getLeadingAnnotations().stream()
                    .anyMatch(annotation -> TypeUtils.isOfClassType(annotation.getType(), AUTOWIRED));
        }


        private J.@Nullable Annotation getQualifierAnnotation(J.VariableDeclarations field) {
}
                    .filter(annotation -> TypeUtils.isOfClassType(annotation.getType(), QUALIFIER))
                    .findFirst()
                    .orElse(null);
        }
    }
}
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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
public class FieldInjectionToConstructorInjection extends Recipe {
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
        private final @Nullable Integer maxAutowiredFields;

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

            // Check if any fields have @Qualifier annotations
            boolean hasQualifierAnnotations = autowiredFields.stream()
                    .anyMatch(field -> field.getLeadingAnnotations().stream()
                            .anyMatch(annotation -> TypeUtils.isOfClassType(annotation.getType(), QUALIFIER)));

            // Add import for @Qualifier if needed
            if (hasQualifierAnnotations) {
                maybeAddImport(QUALIFIER);
            }

            // Process the class
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            // Create a new constructor or update existing one
            if (constructors.isEmpty()) {
                doAfterVisit(new CreateConstructorVisitor(cd, autowiredFields));
            } else {
                doAfterVisit(new UpdateConstructorVisitor(cd, constructors.get(0), autowiredFields));
            }

            // Remove @Autowired annotations from fields
            for (J.VariableDeclarations field : autowiredFields) {
                doAfterVisit(new RemoveAutowiredAnnotationVisitor(field));
            }

            return cd;
        }

        private boolean hasAutowiredAnnotation(J.VariableDeclarations varDecl) {
            return varDecl.getLeadingAnnotations().stream()
                    .anyMatch(annotation -> TypeUtils.isOfClassType(annotation.getType(), AUTOWIRED));
        }
    }

    private static class CreateConstructorVisitor extends JavaVisitor<ExecutionContext> {
        private static final Comparator<Statement> CONSTRUCTORS_FIRST_COMPARATOR = (a, b) -> {
            boolean aIsMethod = a instanceof J.MethodDeclaration;
            boolean bIsMethod = b instanceof J.MethodDeclaration;

            if (aIsMethod != bIsMethod) {
                return aIsMethod ? 1 : -1; // non-methods first
            }

            if (aIsMethod) {
                boolean aIsConstructor = ((J.MethodDeclaration) a).isConstructor();
                boolean bIsConstructor = ((J.MethodDeclaration) b).isConstructor();
                return Boolean.compare(bIsConstructor, aIsConstructor); // constructors first
            }

            return 0; // equal for non-methods
        };
        private final J.ClassDeclaration classDecl;
        private final List<J.VariableDeclarations> autowiredFields;

        public CreateConstructorVisitor(J.ClassDeclaration classDecl, List<J.VariableDeclarations> autowiredFields) {
            this.classDecl = classDecl;
            this.autowiredFields = autowiredFields;
        }

        @Override
        public J visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
            if (cd.getId().equals(classDecl.getId())) {
                // Build constructor parameters
                StringBuilder constructorParams = new StringBuilder();
                for (int i = 0; i < autowiredFields.size(); i++) {
                    J.VariableDeclarations field = autowiredFields.get(i);
                    J.Identifier name = field.getVariables().get(0).getName();
                    TypeTree typeExpr = field.getTypeExpression();

                    if (i > 0) {
                        constructorParams.append(", ");
                    }

                    // Add @Qualifier annotation if present
                    J.Annotation qualifierAnnotation = getQualifierAnnotation(field);
                    if (qualifierAnnotation != null) {
                        constructorParams.append(qualifierAnnotation.printTrimmed()).append(" ");
                    }

                    constructorParams.append(typeExpr.printTrimmed()).append(" ").append(name.getSimpleName());
                }

                // Build constructor body
                StringBuilder constructorBody = new StringBuilder();
                for (J.VariableDeclarations field : autowiredFields) {
                    J.Identifier name = field.getVariables().get(0).getName();
                    constructorBody.append("this.").append(name.getSimpleName()).append(" = ").append(name.getSimpleName()).append(";\n");
                }

                // Create constructor template
                String constructorTemplate = "public " + cd.getSimpleName() + "(" + constructorParams + ") {\n" + constructorBody + "}";

                // Apply template
                JavaTemplate template = JavaTemplate.builder(constructorTemplate)
                        .contextSensitive()
                        .build();

                return template.apply(getCursor(), cd.getBody().getCoordinates().addStatement(CONSTRUCTORS_FIRST_COMPARATOR));
            }
            return super.visitClassDeclaration(cd, ctx);
        }

        private J.@Nullable Annotation getQualifierAnnotation(J.VariableDeclarations field) {
            return field.getLeadingAnnotations().stream()
                    .filter(annotation -> TypeUtils.isOfClassType(annotation.getType(), QUALIFIER))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static class UpdateConstructorVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.ClassDeclaration classDecl;
        private final J.MethodDeclaration constructor;
        private final List<J.VariableDeclarations> autowiredFields;

        public UpdateConstructorVisitor(J.ClassDeclaration classDecl, J.MethodDeclaration constructor, List<J.VariableDeclarations> autowiredFields) {
            this.classDecl = classDecl;
            this.constructor = constructor;
            this.autowiredFields = autowiredFields;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
            if (cd.getId().equals(classDecl.getId())) {
                // Create a new constructor with parameters
                StringBuilder constructorTemplate = new StringBuilder("public " + cd.getSimpleName() + "(");

                // Add parameters
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

                // Add body
                constructorTemplate.append(") {\n");
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

                // Find the existing constructor
                List<Statement> statements = new ArrayList<>(cd.getBody().getStatements());
                int constructorIndex = -1;
                for (int i = 0; i < statements.size(); i++) {
                    Statement statement = statements.get(i);
                    if (statement instanceof J.MethodDeclaration) {
                        J.MethodDeclaration method = (J.MethodDeclaration) statement;
                        if (method.getId().equals(constructor.getId())) {
                            constructorIndex = i;
                            break;
                        }
                    }
                }

                // Replace the existing constructor with the new one
                if (constructorIndex >= 0) {
                    Statement existingConstructor = statements.get(constructorIndex);
                    statements.set(constructorIndex, constructorJavaTemplate.apply(
                        getCursor(),
                        existingConstructor.getCoordinates().replace()
                    ));

                    return cd.withBody(cd.getBody().withStatements(statements));
                }
            }
            return super.visitClassDeclaration(cd, ctx);
        }

        private J.@Nullable Annotation getQualifierAnnotation(J.VariableDeclarations field) {
            return field.getLeadingAnnotations().stream()
                    .filter(annotation -> TypeUtils.isOfClassType(annotation.getType(), QUALIFIER))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static class RemoveAutowiredAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.VariableDeclarations field;

        public RemoveAutowiredAnnotationVisitor(J.VariableDeclarations field) {
            this.field = field;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            return maybeAutoFormat(classDecl, cd, ctx);
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations varDecl, ExecutionContext ctx) {
            if (varDecl.getId().equals(field.getId())) {
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
                    for (int i = 0; i < modifiers.size(); i++) {
                        if (modifiers.get(i).getType() == J.Modifier.Type.Private) {
                            privateIndex = i;
                            break;
                        }
                    }

                    J.Modifier finalModifier = new J.Modifier(
                        Tree.randomId(),
                        Space.SINGLE_SPACE,
                        Markers.EMPTY,
                        null,
                        J.Modifier.Type.Final,
                        emptyList()
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

                // Remove imports for @Autowired if no longer used
                maybeRemoveImport(AUTOWIRED);

                return maybeAutoFormat(varDecl, vd, ctx);
            }
            return super.visitVariableDeclarations(varDecl, ctx);
        }
    }
}

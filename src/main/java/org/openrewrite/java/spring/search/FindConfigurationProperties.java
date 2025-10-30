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
package org.openrewrite.java.spring.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.spring.table.ConfigurationPropertiesTable;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindConfigurationProperties extends Recipe {

    transient ConfigurationPropertiesTable configProperties = new ConfigurationPropertiesTable(this);

    @Override
    public String getDisplayName() {
        return "Find Spring `@ConfigurationProperties`";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Find all classes annotated with `@ConfigurationProperties` and extract their prefix values. " +
                "This is useful for discovering all externalized configuration properties in Spring Boot applications.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

                for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                    if (TypeUtils.isOfClassType(annotation.getType(), "org.springframework.boot.context.properties.ConfigurationProperties")) {
                        PrefixInfo prefixInfo = extractPrefix(annotation, classDecl);
                        String classType = requireNonNull(classDecl.getType()).getFullyQualifiedName();
                        String sourcePath = getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString();

                        configProperties.insertRow(ctx, new ConfigurationPropertiesTable.Row(
                                sourcePath,
                                classType,
                                prefixInfo.value
                        ));

                        String marker = prefixInfo.isConstant ?
                                "@ConfigurationProperties(" + prefixInfo.source + " = \"" + prefixInfo.value + "\")" :
                                "@ConfigurationProperties(\"" + prefixInfo.value + "\")";
                        c = c.withLeadingAnnotations(ListUtils.map(c.getLeadingAnnotations(),
                                a -> a == annotation ? SearchResult.found(a, marker) : a));
                        break;
                    }
                }

                return c;
            }

            class PrefixInfo {
                final String value;
                final String source;
                final boolean isConstant;

                PrefixInfo(String value, String source, boolean isConstant) {
                    this.value = value;
                    this.source = source;
                    this.isConstant = isConstant;
                }
            }

            private PrefixInfo extractPrefix(J.Annotation annotation, J.ClassDeclaration classDecl) {
                if (annotation.getArguments() == null || annotation.getArguments().isEmpty()) {
                    return new PrefixInfo("", "", false);
                }

                for (Expression arg : annotation.getArguments()) {
                    if (arg instanceof J.Literal) {
                        // Handle @ConfigurationProperties("prefix")
                        Object value = ((J.Literal) arg).getValue();
                        String prefix = value != null ? value.toString() : "";
                        return new PrefixInfo(prefix, prefix, false);
                    }
                    if (arg instanceof J.Identifier) {
                        // Handle @ConfigurationProperties(PREFIX)
                        return resolveFieldValue((J.Identifier) arg, classDecl);
                    }
                    if (arg instanceof J.FieldAccess) {
                        // Handle @ConfigurationProperties(SomeClass.PREFIX)
                        return resolveFieldAccessValue((J.FieldAccess) arg);
                    }
                    if (arg instanceof J.Assignment) {
                        // Handle @ConfigurationProperties(value = "prefix") or @ConfigurationProperties(prefix = "prefix")
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier) {
                            String paramName = ((J.Identifier) assignment.getVariable()).getSimpleName();
                            if ("value".equals(paramName) || "prefix".equals(paramName)) {
                                Expression assignmentValue = assignment.getAssignment();
                                if (assignmentValue instanceof J.Literal) {
                                    Object value = ((J.Literal) assignmentValue).getValue();
                                    String prefix = value != null ? value.toString() : "";
                                    return new PrefixInfo(prefix, prefix, false);
                                }
                                if (assignmentValue instanceof J.Identifier) {
                                    return resolveFieldValue((J.Identifier) assignmentValue, classDecl);
                                }
                                if (assignmentValue instanceof J.FieldAccess) {
                                    return resolveFieldAccessValue((J.FieldAccess) assignmentValue);
                                }
                            }
                        }
                    }
                }

                return new PrefixInfo("", "", false);
            }

            private PrefixInfo resolveFieldValue(J.Identifier identifier, J.ClassDeclaration classDecl) {
                String fieldName = identifier.getSimpleName();

                // Look for the field in the class
                for (J statement : classDecl.getBody().getStatements()) {
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations varDecls = (J.VariableDeclarations) statement;
                        for (J.VariableDeclarations.NamedVariable variable : varDecls.getVariables()) {
                            if (fieldName.equals(variable.getSimpleName()) && variable.getInitializer() instanceof J.Literal) {
                                Object value = ((J.Literal) variable.getInitializer()).getValue();
                                String prefix = value != null ? value.toString() : "";
                                return new PrefixInfo(prefix, fieldName, true);
                            }
                        }
                    }
                }

                return new PrefixInfo("", fieldName, true);
            }

            private PrefixInfo resolveFieldAccessValue(J.FieldAccess fieldAccess) {
                // For now, just return empty string for qualified field access
                // A more complete implementation would resolve the field from the referenced class
                String fieldName = fieldAccess.getSimpleName();
                return new PrefixInfo("", fieldName, true);
            }
        };
    }
}

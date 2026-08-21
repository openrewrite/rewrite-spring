/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.data.search;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;

import static org.openrewrite.java.spring.data.search.FindMissingMongoValueRepresentation.*;

final class MongoValueRepresentationScanner {

    private static final MethodMatcher UUID_REPRESENTATION =
            new MethodMatcher("com.mongodb.MongoClientSettings$Builder uuidRepresentation(..)");
    private static final MethodMatcher BIG_NUMBER_REPRESENTATION = new MethodMatcher(
            "org.springframework.data.mongodb.core.convert.MongoCustomConversions$MongoConverterConfigurationAdapter bigDecimal(..)");
    private static final AnnotationMatcher DOCUMENT =
            new AnnotationMatcher("@org.springframework.data.mongodb.core.mapping.Document");
    private static final AnnotationMatcher FIELD =
            new AnnotationMatcher("@org.springframework.data.mongodb.core.mapping.Field");
    private static final AnnotationMatcher MONGO_ID =
            new AnnotationMatcher("@org.springframework.data.mongodb.core.mapping.MongoId");
    private static final AnnotationMatcher DB_REF =
            new AnnotationMatcher("@org.springframework.data.mongodb.core.mapping.DBRef");
    private static final AnnotationMatcher DOCUMENT_REFERENCE =
            new AnnotationMatcher("@org.springframework.data.mongodb.core.mapping.DocumentReference");
    private static final AnnotationMatcher TRANSIENT =
            new AnnotationMatcher("@org.springframework.data.annotation.Transient");
    private static final AnnotationMatcher ID =
            new AnnotationMatcher("@org.springframework.data.annotation.Id");

    private MongoValueRepresentationScanner() {
    }

    static void scan(SourceFile source, JavaProject project, Accumulator acc, ExecutionContext ctx) {
        if (source.getMarkers().findFirst(ProjectDiagnostic.class).isPresent()) {
            acc.finalizedProjects.add(project);
        }
        if (source.getMarkers().findFirst(RowsRecorded.class).isPresent()) {
            acc.rowsInsertedProjects.add(project);
        }
        if (source instanceof JavaSourceFile && SpringConfigFileSupport.isMainSource(source)) {
            scanJavaConfiguration((JavaSourceFile) source, project, acc, ctx);
            scanAffectedFields((JavaSourceFile) source, project, acc, ctx);
        } else if (SpringConfigFileSupport.isMainSpringConfigurationFile(source)) {
            scanPropertyConfiguration(source, project, acc);
            acc.preferredConfigurationSource.merge(project, source.getSourcePath(),
                    SpringConfigFileSupport::preferredConfigurationSource);
        }
    }

    private static void scanJavaConfiguration(JavaSourceFile source, JavaProject project, Accumulator acc,
                                               ExecutionContext ctx) {
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation m = super.visitMethodInvocation(method, p);
                if (UUID_REPRESENTATION.matches(m)) {
                    recordJavaConfigurationAttempt(ValueKind.UUID, m, source, project, acc);
                }
                if (BIG_NUMBER_REPRESENTATION.matches(m)) {
                    recordJavaConfigurationAttempt(ValueKind.BIG_NUMBER, m, source, project, acc);
                }
                return m;
            }
        }.visit(source, ctx);
    }

    /**
     * A matching call always counts as an attempt, whether its argument is valid — a faulty
     * call (e.g. {@code uuidRepresentation(null)}) still means the project picked Java configuration
     * for this kind, so it should be marked in place rather than suggested via a properties file.
     */
    private static void recordJavaConfigurationAttempt(ValueKind kind, J.MethodInvocation method,
                                                        JavaSourceFile source, JavaProject project, Accumulator acc) {
        acc.markJavaAttempted(kind, project);
        if (hasExplicitArgument(method)) {
            acc.markConfigured(kind, project);
        } else {
            acc.addConfigurationIssue(project, new ConfigurationIssue(source.getSourcePath(), method.getId(), kind));
        }
    }

    private static void scanAffectedFields(JavaSourceFile source, JavaProject project, Accumulator acc,
                                            ExecutionContext ctx) {
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                     ExecutionContext p) {
                J.VariableDeclarations d = super.visitVariableDeclarations(declarations, p);
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (owner == null || getCursor().firstEnclosing(J.MethodDeclaration.class) != null ||
                        !isExplicitlyMongoMapped(owner, d) || isIgnoredField(d)) {
                    return d;
                }
                String owningType = owner.getType() == null ? owner.getSimpleName() :
                        owner.getType().getFullyQualifiedName();
                boolean uuid = containsPersistedType(d.getType(), UUID_TYPE);
                boolean bigNumber = (containsPersistedType(d.getType(), BIG_DECIMAL_TYPE) ||
                        containsPersistedType(d.getType(), BIG_INTEGER_TYPE)) && !hasExplicitFieldTargetType(d);
                for (J.VariableDeclarations.NamedVariable variable : d.getVariables()) {
                    if (uuid) {
                        acc.addOccurrence(project, new Occurrence(source.getSourcePath(), owner.getId(), owningType,
                                variable.getSimpleName(), ValueKind.UUID));
                    }
                    if (bigNumber && !isExcludedBigIntegerId(d, variable)) {
                        acc.addOccurrence(project, new Occurrence(source.getSourcePath(), owner.getId(), owningType,
                                variable.getSimpleName(), ValueKind.BIG_NUMBER));
                    }
                }
                return d;
            }
        }.visit(source, ctx);
    }

    private static void scanPropertyConfiguration(SourceFile source, JavaProject project, Accumulator acc) {
        for (ValueKind kind : ValueKind.values()) {
            if (source instanceof Properties.File) {
                scanPropertiesProperty((Properties.File) source, project, acc, kind);
            } else if (source instanceof Yaml.Documents) {
                scanYamlProperty((Yaml.Documents) source, project, acc, kind);
            }
        }
    }

    private static void scanPropertiesProperty(Properties.File file, JavaProject project, Accumulator acc,
                                                ValueKind kind) {
        Set<Properties.Entry> entries = FindProperties.find(file, kind.configurationProperty, true);
        if (entries.isEmpty()) {
            return;
        }
        acc.markPropertyAttempted(kind, project);
        for (Properties.Entry entry : entries) {
            if (kind.isConfiguredValue(entry.getValue().getText())) {
                acc.markConfigured(kind, project);
            } else {
                acc.addConfigurationIssue(project,
                        new ConfigurationIssue(file.getSourcePath(), entry.getId(), kind));
            }
        }
    }

    private static void scanYamlProperty(Yaml.Documents documents, JavaProject project, Accumulator acc,
                                         ValueKind kind) {
        Set<Yaml.Block> values = FindProperty.find(documents, kind.configurationProperty, true);
        if (values.isEmpty()) {
            return;
        }
        acc.markPropertyAttempted(kind, project);
        for (Yaml.Block value : values) {
            if (value instanceof Yaml.Scalar && kind.isConfiguredValue(((Yaml.Scalar) value).getValue())) {
                acc.markConfigured(kind, project);
            } else {
                acc.addConfigurationIssue(project,
                        new ConfigurationIssue(documents.getSourcePath(), value.getId(), kind));
            }
        }
    }

    private static boolean hasExplicitArgument(J.MethodInvocation method) {
        if (method.getArguments().isEmpty()) {
            return false;
        }
        Expression argument = method.getArguments().get(0);
        return !(argument instanceof J.Literal && ((J.Literal) argument).getValue() == null) &&
                !isUnspecified(argument);
    }

    private static boolean isUnspecified(Expression expression) {
        String name = simpleName(expression);
        if (name == null && expression instanceof J.Literal && ((J.Literal) expression).getValue() != null) {
            name = ((J.Literal) expression).getValue().toString();
        }
        return name != null && "unspecified".equalsIgnoreCase(name.trim());
    }

    private static boolean isExplicitlyMongoMapped(J.ClassDeclaration owner, J.VariableDeclarations declarations) {
        return owner.getLeadingAnnotations().stream().anyMatch(DOCUMENT::matches) ||
                declarations.getLeadingAnnotations().stream().anyMatch(annotation ->
                        FIELD.matches(annotation) || MONGO_ID.matches(annotation) || DB_REF.matches(annotation) ||
                                DOCUMENT_REFERENCE.matches(annotation));
    }

    private static boolean isIgnoredField(J.VariableDeclarations declarations) {
        return declarations.hasModifier(J.Modifier.Type.Static) ||
                declarations.hasModifier(J.Modifier.Type.Transient) ||
                declarations.getLeadingAnnotations().stream().anyMatch(TRANSIENT::matches);
    }

    private static boolean isExcludedBigIntegerId(J.VariableDeclarations declarations,
                                                   J.VariableDeclarations.NamedVariable variable) {
        return TypeUtils.isOfClassType(declarations.getType(), BIG_INTEGER_TYPE) && isBigIntegerId(declarations, variable);
    }

    private static boolean isBigIntegerId(J.VariableDeclarations declarations,
                                          J.VariableDeclarations.NamedVariable variable) {
        return declarations.getLeadingAnnotations().stream().anyMatch(annotation ->
                ID.matches(annotation) || MONGO_ID.matches(annotation)) || "id".equals(variable.getSimpleName());
    }

    private static boolean hasExplicitFieldTargetType(J.VariableDeclarations declarations) {
        for (J.Annotation annotation : declarations.getLeadingAnnotations()) {
            if (!FIELD.matches(annotation) || annotation.getArguments() == null) {
                continue;
            }
            for (Expression argument : annotation.getArguments()) {
                if (argument instanceof J.Assignment) {
                    J.Assignment assignment = (J.Assignment) argument;
                    if (assignment.getVariable() instanceof J.Identifier &&
                            "targetType".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                        String targetType = simpleName(assignment.getAssignment());
                        return targetType != null && !"implicit".equalsIgnoreCase(targetType);
                    }
                }
            }
        }
        return false;
    }

    private static @Nullable String simpleName(Expression expression) {
        if (expression instanceof J.Identifier) {
            return ((J.Identifier) expression).getSimpleName();
        }
        return expression instanceof J.FieldAccess ? ((J.FieldAccess) expression).getSimpleName() : null;
    }

    private static boolean containsPersistedType(@Nullable JavaType type, String fullyQualifiedType) {
        if (type == null) {
            return false;
        }
        if (TypeUtils.isOfClassType(type, fullyQualifiedType)) {
            return true;
        }
        if (type instanceof JavaType.Array) {
            return containsPersistedType(((JavaType.Array) type).getElemType(), fullyQualifiedType);
        }
        if (type instanceof JavaType.Parameterized) {
            JavaType.Parameterized p = (JavaType.Parameterized) type;
            int first = TypeUtils.isAssignableTo("java.util.Map", p.getType()) ? 1 : 0;
            for (int i = first; i < p.getTypeParameters().size(); i++) {
                if (containsPersistedType(p.getTypeParameters().get(i), fullyQualifiedType)) {
                    return true;
                }
            }
        }
        return false;
    }
}

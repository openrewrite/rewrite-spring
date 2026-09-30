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
package org.openrewrite.java.spring.data;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.data.MongoValueRepresentationAccumulator.ConfigurationIssue;
import org.openrewrite.java.spring.data.MongoValueRepresentationAccumulator.Occurrence;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;

/**
 * Detects MongoDB-mapped UUID/BigDecimal/BigInteger fields that need an explicit value-representation
 * configuration under Spring Data MongoDB 5, and how far each project's configuration already gets
 * there, recording everything into a {@link MongoValueRepresentationAccumulator}.
 */
public final class MongoValueRepresentationScanner {

    private static final String UUID_TYPE = "java.util.UUID";
    private static final String BIG_DECIMAL_TYPE = "java.math.BigDecimal";
    private static final String BIG_INTEGER_TYPE = "java.math.BigInteger";

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
    private static final AnnotationMatcher PROFILE =
            new AnnotationMatcher("@org.springframework.context.annotation.Profile");

    private MongoValueRepresentationScanner() {
    }

    /**
     * A compilation unit worth visiting: it persists a UUID/BigDecimal/BigInteger field, or
     * configures one via a client-settings/converter call — either can appear with no trace of the
     * other, so neither alone would be a safe precondition on its own. Shared by both recipes'
     * Java-side visitors.
     */
    public static TreeVisitor<?, ExecutionContext> javaPrecondition() {
        return Preconditions.or(
                new UsesType<>(UUID_TYPE, false),
                new UsesType<>(BIG_DECIMAL_TYPE, false),
                new UsesType<>(BIG_INTEGER_TYPE, false),
                new UsesMethod<>(UUID_REPRESENTATION),
                new UsesMethod<>(BIG_NUMBER_REPRESENTATION));
    }

    /**
     * Narrower than {@link #javaPrecondition()}: for a visitor that only inspects method
     * invocations (never persisted fields), a client-settings/converter call is the only signal
     * that matters.
     */
    public static TreeVisitor<?, ExecutionContext> javaConfigurationPrecondition() {
        return Preconditions.or(
                new UsesMethod<>(UUID_REPRESENTATION),
                new UsesMethod<>(BIG_NUMBER_REPRESENTATION));
    }

    /**
     * A single scanner covering all three source kinds a project's configuration can live in: Java
     * (client settings/converter calls, and the persisted fields themselves), properties, and YAML.
     * A source file is exactly one of these, so this one dispatch is the only type check needed —
     * each delegate below is a single, self-contained visitor rather than several chained together.
     */
    public static TreeVisitor<?, ExecutionContext> scanner(MongoValueRepresentationAccumulator acc) {
        TreeVisitor<?, ExecutionContext> javaVisitor = Preconditions.check(javaPrecondition(), javaScanner(acc));
        PropertiesIsoVisitor<ExecutionContext> propertiesVisitor = propertiesScanner(acc);
        YamlIsoVisitor<ExecutionContext> yamlVisitor = yamlScanner(acc);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof J.CompilationUnit) {
                    return javaVisitor.visit(tree, ctx);
                }
                if (tree instanceof Properties.File) {
                    return propertiesVisitor.visit(tree, ctx);
                }
                if (tree instanceof Yaml.Documents) {
                    return yamlVisitor.visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    private static JavaIsoVisitor<ExecutionContext> javaScanner(MongoValueRepresentationAccumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                JavaProject project = SpringConfigFileSupport.javaProject(cu);
                return project == null || !SpringConfigFileSupport.isMainSource(cu) ?
                        cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visitedMethod = super.visitMethodInvocation(method, ctx);
                JavaProject project = SpringConfigFileSupport.javaProject(getCursor().firstEnclosingOrThrow(J.CompilationUnit.class));
                if (project == null) {
                    return visitedMethod;
                }
                JavaSourceFile source = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);
                boolean profileGated = isProfileGated(getCursor());
                if (UUID_REPRESENTATION.matches(visitedMethod)) {
                    recordJavaConfigurationAttempt(MongoValueRepresentationKind.UUID, visitedMethod, source, project, acc, profileGated);
                }
                if (BIG_NUMBER_REPRESENTATION.matches(visitedMethod)) {
                    recordJavaConfigurationAttempt(MongoValueRepresentationKind.BIG_NUMBER, visitedMethod, source, project, acc, profileGated);
                }
                return visitedMethod;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                     ExecutionContext ctx) {
                J.VariableDeclarations visitedDeclarations = super.visitVariableDeclarations(declarations, ctx);
                JavaSourceFile source = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);
                JavaProject project = SpringConfigFileSupport.javaProject(source);
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (project == null || owner == null || getCursor().firstEnclosing(J.MethodDeclaration.class) != null ||
                        !isExplicitlyMongoMapped(owner, visitedDeclarations) || isIgnoredField(visitedDeclarations)) {
                    return visitedDeclarations;
                }
                String owningType = owner.getType() == null ? owner.getSimpleName() :
                        owner.getType().getFullyQualifiedName();
                boolean uuid = containsPersistedType(visitedDeclarations.getType(), UUID_TYPE);
                boolean bigNumber = (containsPersistedType(visitedDeclarations.getType(), BIG_DECIMAL_TYPE) ||
                        containsPersistedType(visitedDeclarations.getType(), BIG_INTEGER_TYPE)) &&
                        !hasExplicitFieldTargetType(visitedDeclarations);
                for (J.VariableDeclarations.NamedVariable variable : visitedDeclarations.getVariables()) {
                    if (uuid) {
                        acc.addOccurrence(project, new Occurrence(source.getSourcePath(), owner.getId(), owningType,
                                variable.getSimpleName(), MongoValueRepresentationKind.UUID));
                    }
                    if (bigNumber && !isExcludedBigIntegerId(visitedDeclarations, variable)) {
                        acc.addOccurrence(project, new Occurrence(source.getSourcePath(), owner.getId(), owningType,
                                variable.getSimpleName(), MongoValueRepresentationKind.BIG_NUMBER));
                    }
                }
                return visitedDeclarations;
            }
        };
    }

    private static PropertiesIsoVisitor<ExecutionContext> propertiesScanner(MongoValueRepresentationAccumulator acc) {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.File visitFile(Properties.File file, ExecutionContext ctx) {
                JavaProject project = SpringConfigFileSupport.javaProject(file);
                if (project == null || !SpringConfigFileSupport.isMainSpringConfigurationFile(file)) {
                    return file;
                }
                boolean baseFile = SpringConfigFileSupport.isBaseConfigurationFile(file.getSourcePath());
                for (MongoValueRepresentationKind kind : MongoValueRepresentationKind.values()) {
                    scanPropertiesProperty(file, project, acc, kind, kind.configurationProperty, baseFile);
                    if (kind.legacyConfigurationProperty != null) {
                        scanPropertiesProperty(file, project, acc, kind, kind.legacyConfigurationProperty, baseFile);
                    }
                }
                if (baseFile) {
                    acc.mergePreferredConfigurationSource(project, file.getSourcePath());
                }
                return file;
            }
        };
    }

    private static YamlIsoVisitor<ExecutionContext> yamlScanner(MongoValueRepresentationAccumulator acc) {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                JavaProject project = SpringConfigFileSupport.javaProject(documents);
                if (project == null || !SpringConfigFileSupport.isMainSpringConfigurationFile(documents)) {
                    return documents;
                }
                boolean baseFile = SpringConfigFileSupport.isBaseConfigurationFile(documents.getSourcePath());
                for (MongoValueRepresentationKind kind : MongoValueRepresentationKind.values()) {
                    scanYamlProperty(documents, project, acc, kind, kind.configurationProperty, baseFile);
                    if (kind.legacyConfigurationProperty != null) {
                        scanYamlProperty(documents, project, acc, kind, kind.legacyConfigurationProperty, baseFile);
                    }
                }
                if (baseFile) {
                    acc.mergePreferredConfigurationSource(project, documents.getSourcePath());
                }
                return documents;
            }
        };
    }

    /**
     * A matching call counts as an attempt that satisfies the project, unless it's behind an
     * {@code @Profile}: that only applies conditionally, so a base properties-file suggestion must
     * still be offered for every other profile. A faulty call (e.g. {@code uuidRepresentation(null)})
     * is flagged in place either way, since that's a real bug regardless of which profile runs it.
     */
    private static void recordJavaConfigurationAttempt(MongoValueRepresentationKind kind, J.MethodInvocation method,
                                                         JavaSourceFile source, JavaProject project,
                                                         MongoValueRepresentationAccumulator acc, boolean profileGated) {
        boolean explicitArgument = hasExplicitArgument(method);
        if (!explicitArgument) {
            acc.addConfigurationIssue(project, new ConfigurationIssue(source.getSourcePath(), method.getId(), kind));
        }
        if (profileGated) {
            return;
        }
        acc.markJavaAttempted(kind, project);
        if (explicitArgument) {
            acc.markConfigured(kind, project);
        }
    }

    /**
     * Whether the method invocation's enclosing {@code @Bean} method or {@code @Configuration} class
     * carries {@code @Profile}, meaning the configuration it applies isn't guaranteed for every project run.
     */
    private static boolean isProfileGated(Cursor cursor) {
        J.MethodDeclaration enclosingMethod = cursor.firstEnclosing(J.MethodDeclaration.class);
        if (enclosingMethod != null && enclosingMethod.getLeadingAnnotations().stream().anyMatch(PROFILE::matches)) {
            return true;
        }
        J.ClassDeclaration enclosingClass = cursor.firstEnclosing(J.ClassDeclaration.class);
        return enclosingClass != null && enclosingClass.getLeadingAnnotations().stream().anyMatch(PROFILE::matches);
    }

    /**
     * A value found here counts as configuring the project, unless the file is profile-specific:
     * that only loads when its profile is active, so a base suggestion must still be offered for
     * every other profile. An invalid value is flagged in place either way, since that's a real
     * bug regardless of which profile loads it.
     */
    private static void scanPropertiesProperty(Properties.File file, JavaProject project, MongoValueRepresentationAccumulator acc,
                                                MongoValueRepresentationKind kind, String propertyKey, boolean baseFile) {
        Set<Properties.Entry> entries = FindProperties.find(file, propertyKey, true);
        if (entries.isEmpty()) {
            return;
        }
        if (baseFile) {
            acc.markPropertyAttempted(kind, project);
        }
        for (Properties.Entry entry : entries) {
            if (kind.isConfiguredValue(entry.getValue().getText())) {
                if (baseFile) {
                    acc.markConfigured(kind, project);
                }
            } else {
                acc.addConfigurationIssue(project, new ConfigurationIssue(file.getSourcePath(), entry.getId(), kind));
            }
        }
    }

    private static void scanYamlProperty(Yaml.Documents documents, JavaProject project, MongoValueRepresentationAccumulator acc,
                                          MongoValueRepresentationKind kind, String propertyKey, boolean baseFile) {
        Set<Yaml.Block> values = FindProperty.find(documents, propertyKey, true);
        if (values.isEmpty()) {
            return;
        }
        if (baseFile) {
            acc.markPropertyAttempted(kind, project);
        }
        for (Yaml.Block value : values) {
            if (value instanceof Yaml.Scalar && kind.isConfiguredValue(((Yaml.Scalar) value).getValue())) {
                if (baseFile) {
                    acc.markConfigured(kind, project);
                }
            } else {
                acc.addConfigurationIssue(project, new ConfigurationIssue(documents.getSourcePath(), value.getId(), kind));
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
            JavaType.Parameterized parameterizedType = (JavaType.Parameterized) type;
            int first = TypeUtils.isAssignableTo("java.util.Map", parameterizedType.getType()) ? 1 : 0;
            for (int i = first; i < parameterizedType.getTypeParameters().size(); i++) {
                if (containsPersistedType(parameterizedType.getTypeParameters().get(i), fullyQualifiedType)) {
                    return true;
                }
            }
        }
        return false;
    }
}

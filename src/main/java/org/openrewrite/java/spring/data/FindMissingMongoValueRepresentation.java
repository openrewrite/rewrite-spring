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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Find MongoDB-persisted UUID and big-number fields for which Spring Data MongoDB 5
 * no longer supplies a default representation.
 */
public class FindMissingMongoValueRepresentation extends ScanningRecipe<FindMissingMongoValueRepresentation.Accumulator> {

    private static final String UUID_PROPERTY = "spring.mongodb.representation.uuid";
    private static final String BIG_DECIMAL_PROPERTY = "spring.data.mongodb.representation.big-decimal";

    private static final String UUID_TYPE = "java.util.UUID";
    private static final String BIG_DECIMAL_TYPE = "java.math.BigDecimal";
    private static final String BIG_INTEGER_TYPE = "java.math.BigInteger";

    private static final String MONGO_CLIENT_SETTINGS_BUILDER = "com.mongodb.MongoClientSettings.Builder";
    private static final String MONGO_CONVERTER_CONFIGURATION_ADAPTER =
            "org.springframework.data.mongodb.core.convert.MongoCustomConversions.MongoConverterConfigurationAdapter";

    private static final AnnotationMatcher DOCUMENT =
            new AnnotationMatcher("@org.springframework.data.mongodb.core.mapping.Document");
    private static final AnnotationMatcher PERSISTENT =
            new AnnotationMatcher("@org.springframework.data.annotation.Persistent");
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

    @Getter
    final String displayName = "Find missing MongoDB value representation configuration";

    @Getter
    final String description = "Find MongoDB-persisted UUID, BigInteger, and BigDecimal fields that require an explicit " +
            "representation when migrating to Spring Data MongoDB 5. The recipe reports the affected fields without " +
            "choosing a storage representation.";

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }

                SourceFile source = (SourceFile) tree;
                Optional<JavaProject> javaProject = source.getMarkers().findFirst(JavaProject.class);
                if (!javaProject.isPresent()) {
                    return source;
                }

                RepresentationConfiguration configuration = acc.projects.computeIfAbsent(
                        javaProject.get(), ignored -> new RepresentationConfiguration());

                if (source instanceof JavaSourceFile) {
                    new JavaIsoVisitor<RepresentationConfiguration>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, RepresentationConfiguration config) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, config);
                            if (isExplicitUuidConfiguration(m)) {
                                config.uuidConfigured.set(true);
                            } else if (isExplicitBigNumberConfiguration(m)) {
                                config.bigNumberConfigured.set(true);
                            }
                            return m;
                        }
                    }.visit(source, configuration);
                } else if (source instanceof Properties.File) {
                    if (hasExplicitProperty((Properties.File) source, UUID_PROPERTY)) {
                        configuration.uuidConfigured.set(true);
                    }
                    if (hasExplicitProperty((Properties.File) source, BIG_DECIMAL_PROPERTY)) {
                        configuration.bigNumberConfigured.set(true);
                    }
                } else if (source instanceof Yaml.Documents) {
                    if (hasExplicitYamlProperty((Yaml.Documents) source, UUID_PROPERTY)) {
                        configuration.uuidConfigured.set(true);
                    }
                    if (hasExplicitYamlProperty((Yaml.Documents) source, BIG_DECIMAL_PROPERTY)) {
                        configuration.bigNumberConfigured.set(true);
                    }
                }
                return source;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof JavaSourceFile)) {
                    return tree;
                }

                SourceFile source = (SourceFile) tree;
                Optional<JavaProject> javaProject = source.getMarkers().findFirst(JavaProject.class);
                if (!javaProject.isPresent()) {
                    return source;
                }

                RepresentationConfiguration configuration = acc.projects.get(javaProject.get());
                if (configuration == null ||
                        (configuration.uuidConfigured.get() && configuration.bigNumberConfigured.get())) {
                    return source;
                }

                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                             ExecutionContext executionContext) {
                        J.VariableDeclarations d = super.visitVariableDeclarations(declarations, executionContext);

                        J.ClassDeclaration owningClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (owningClass == null || getCursor().firstEnclosing(J.MethodDeclaration.class) != null ||
                                !isMongoPersistent(owningClass, d) || isIgnoredField(d)) {
                            return d;
                        }

                        boolean missingUuid = !configuration.uuidConfigured.get() && containsType(d.getType(), UUID_TYPE);
                        boolean missingBigNumber = !configuration.bigNumberConfigured.get() &&
                                (containsType(d.getType(), BIG_DECIMAL_TYPE) || containsType(d.getType(), BIG_INTEGER_TYPE)) &&
                                !hasExplicitFieldTargetType(d) && !isBigIntegerId(d);

                        if (!missingUuid && !missingBigNumber) {
                            return d;
                        }

                        String message;
                        if (missingUuid && missingBigNumber) {
                            message = "Spring Data MongoDB 5 requires explicit UUID and BigDecimal/BigInteger representations; " +
                                    "configure `" + UUID_PROPERTY + "` and `" + BIG_DECIMAL_PROPERTY + "`, or their Java equivalents.";
                        } else if (missingUuid) {
                            message = "Spring Data MongoDB 5 requires an explicit UUID representation; configure `" +
                                    UUID_PROPERTY + "` or `MongoClientSettings.Builder.uuidRepresentation(...)`.";
                        } else {
                            message = "Spring Data MongoDB 5 requires an explicit BigDecimal/BigInteger representation; configure `" +
                                    BIG_DECIMAL_PROPERTY + "` or `MongoConverterConfigurationAdapter.bigDecimal(...)`.";
                        }
                        return SearchResult.found(d, message);
                    }
                }.visitNonNull(source, ctx);
            }
        };
    }

    private static boolean isExplicitUuidConfiguration(J.MethodInvocation method) {
        return "uuidRepresentation".equals(method.getSimpleName()) &&
                isDeclaredOn(method, MONGO_CLIENT_SETTINGS_BUILDER) && hasExplicitArgument(method);
    }

    private static boolean isExplicitBigNumberConfiguration(J.MethodInvocation method) {
        return "bigDecimal".equals(method.getSimpleName()) &&
                isDeclaredOn(method, MONGO_CONVERTER_CONFIGURATION_ADAPTER) && hasExplicitArgument(method);
    }

    private static boolean isDeclaredOn(J.MethodInvocation method, String expectedType) {
        JavaType.Method methodType = method.getMethodType();
        if (methodType == null) {
            return false;
        }
        JavaType.FullyQualified declaringType = TypeUtils.asFullyQualified(methodType.getDeclaringType());
        return declaringType != null &&
                expectedType.equals(declaringType.getFullyQualifiedName().replace('$', '.'));
    }

    private static boolean hasExplicitArgument(J.MethodInvocation method) {
        return !method.getArguments().isEmpty() && !isUnspecified(method.getArguments().get(0));
    }

    private static boolean isUnspecified(Expression expression) {
        String name = null;
        if (expression instanceof J.Identifier) {
            name = ((J.Identifier) expression).getSimpleName();
        } else if (expression instanceof J.FieldAccess) {
            name = ((J.FieldAccess) expression).getSimpleName();
        } else if (expression instanceof J.Literal && ((J.Literal) expression).getValue() != null) {
            name = ((J.Literal) expression).getValue().toString();
        }
        return name != null && "unspecified".equalsIgnoreCase(name.trim());
    }

    private static boolean hasExplicitProperty(Properties.File file, String property) {
        for (Properties.Entry entry : FindProperties.find(file, property, true)) {
            if (isExplicitValue(entry.getValue().getText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExplicitYamlProperty(Yaml.Documents documents, String property) {
        for (Yaml.Block value : FindProperty.find(documents, property, true)) {
            if (!(value instanceof Yaml.Scalar) || isExplicitValue(((Yaml.Scalar) value).getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExplicitValue(@Nullable String value) {
        return value != null && !value.trim().isEmpty() && !"unspecified".equalsIgnoreCase(value.trim());
    }

    private static boolean isMongoPersistent(J.ClassDeclaration owningClass, J.VariableDeclarations declarations) {
        return owningClass.getLeadingAnnotations().stream().anyMatch(annotation ->
                DOCUMENT.matches(annotation) || PERSISTENT.matches(annotation)) ||
                declarations.getLeadingAnnotations().stream().anyMatch(annotation ->
                        FIELD.matches(annotation) || MONGO_ID.matches(annotation) || DB_REF.matches(annotation) ||
                                DOCUMENT_REFERENCE.matches(annotation));
    }

    private static boolean isIgnoredField(J.VariableDeclarations declarations) {
        return declarations.hasModifier(J.Modifier.Type.Static) ||
                declarations.hasModifier(J.Modifier.Type.Transient) ||
                declarations.getLeadingAnnotations().stream().anyMatch(TRANSIENT::matches);
    }

    private static boolean isBigIntegerId(J.VariableDeclarations declarations) {
        if (!containsType(declarations.getType(), BIG_INTEGER_TYPE)) {
            return false;
        }
        if (declarations.getLeadingAnnotations().stream().anyMatch(annotation ->
                ID.matches(annotation) || MONGO_ID.matches(annotation))) {
            return true;
        }
        return declarations.getVariables().stream().anyMatch(variable -> "id".equals(variable.getSimpleName()));
    }

    private static boolean hasExplicitFieldTargetType(J.VariableDeclarations declarations) {
        for (J.Annotation annotation : declarations.getLeadingAnnotations()) {
            if (!FIELD.matches(annotation) || annotation.getArguments() == null) {
                continue;
            }
            for (Expression argument : annotation.getArguments()) {
                if (!(argument instanceof J.Assignment)) {
                    continue;
                }
                J.Assignment assignment = (J.Assignment) argument;
                if (!(assignment.getVariable() instanceof J.Identifier) ||
                        !"targetType".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                    continue;
                }
                String targetType = simpleName(assignment.getAssignment());
                return targetType != null && !"implicit".equalsIgnoreCase(targetType);
            }
        }
        return false;
    }

    private static @Nullable String simpleName(Expression expression) {
        if (expression instanceof J.Identifier) {
            return ((J.Identifier) expression).getSimpleName();
        }
        if (expression instanceof J.FieldAccess) {
            return ((J.FieldAccess) expression).getSimpleName();
        }
        return null;
    }

    private static boolean containsType(@Nullable JavaType type, String fullyQualifiedType) {
        if (type == null) {
            return false;
        }
        if (TypeUtils.isOfClassType(type, fullyQualifiedType)) {
            return true;
        }
        if (type instanceof JavaType.Array) {
            return containsType(((JavaType.Array) type).getElemType(), fullyQualifiedType);
        }
        if (type instanceof JavaType.Parameterized) {
            for (JavaType typeParameter : ((JavaType.Parameterized) type).getTypeParameters()) {
                if (containsType(typeParameter, fullyQualifiedType)) {
                    return true;
                }
            }
        }
        return false;
    }

    static class Accumulator {
        final Map<JavaProject, RepresentationConfiguration> projects = new ConcurrentHashMap<>();
    }

    static class RepresentationConfiguration {
        final AtomicBoolean uuidConfigured = new AtomicBoolean();
        final AtomicBoolean bigNumberConfigured = new AtomicBoolean();
    }
}

/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.boot2;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Incubating(since = "4.10.0")
@AllArgsConstructor
public class MarkDeprecatedAPIs extends Recipe {

    @Override
    public String getDisplayName() {
        return "Find and mark java types with a deprecation marker.";
    }

    @Override
    public String getDescription() {
        return "A utility recipe to mark a target class, method, or constant with a deprecation marker.";
    }

    @Option(displayName = "Source of deprecation",
            description = "The source of a deprecation.",
            example = "Spring Boot 2.3.0")
    String sourceOfDeprecation;

    @Option(displayName = "Type information to find the deprecated target",
            description = "Type information determines what type of pointcut expression is used. Types include: class, constructor, classMethod, enum, enumConstant, exception, fieldConstant, interface, interfaceMethod.",
            example = "enum")
    String deprecatedType;

    @Option(displayName = "A search pattern, expressed as a pointcut expression, that is used to find the deprecated target",
            description = "The format of the pointcut expression is based on the deprecatedType.",
            example = "class: com.google.common.collect.ImmutableSet, classMethod: com.google.common.collect.ImmutableSet of(..), or fieldConstant org.openrewrite.Constant.VALUE")
    String searchPattern;

    @Option(displayName = "Descriptive text about the deprecation",
            description = "Optionally, communicate information about the deprecation.",
            required = false,
            example = "in favor org.example.NewClass")
    String deprecationComment;

    @Option(displayName = "Link to the documentation.",
            description = "A link to information about the the documentation.",
            required = false,
            example = "https://www.example.link")
    String deprecatedLink;

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MarkDeprecatedAPIs.FindAndMarkDeprecatedAPIs(
                sourceOfDeprecation,
                deprecatedType,
                searchPattern,
                deprecationComment,
                deprecatedLink
        );
    }

    private static class FindAndMarkDeprecatedAPIs extends JavaIsoVisitor<ExecutionContext> {
        private final Deprecation deprecation;

        FindAndMarkDeprecatedAPIs(
                String sourceOfDeprecation,
                String deprecatedType,
                String searchPattern,
                String deprecationComment,
                String deprecatedLink) {

            Deprecation.Source source = new Deprecation.Source(
                    StringUtils.isNullOrEmpty(sourceOfDeprecation) ? "" : sourceOfDeprecation,
                    Deprecation.DeprecatedType.fromValue(deprecatedType),
                    StringUtils.isNullOrEmpty(deprecationComment) ? "" : deprecationComment,
                    StringUtils.isNullOrEmpty(deprecatedLink) ? "" : deprecatedLink);

            Deprecation.Info info = new Deprecation.Info(Tree.randomId(), source);

            this.deprecation = new Deprecation(searchPattern, info);
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext context) {
            switch (deprecation.info.source.type) {
                case CLASS:
                case ENUM:
                case EXCEPTION:
                case INTERFACE:
                    Set<NameTree> references = FindTypes.find(compilationUnit, deprecation.searchPattern);
                    if (!references.isEmpty()) {
                        getCursor().putMessage(deprecation.info.source.type.name(), references);
                    }
                    break;

                case CLASS_METHOD:
                    Set<J> methods = FindMethods.find(compilationUnit, deprecation.searchPattern);
                    if (!methods.isEmpty()) {
                        getCursor().putMessage(deprecation.info.source.type.name(), methods);
                    }
                    break;

                case FIELD_CONSTANT:
                case ENUM_CONSTANT:
                    Set<NameTree> enumConstants = findConstants(compilationUnit, TypeUtils.asFullyQualified(JavaType.Class.build(deprecation.searchPattern)));
                    if (!enumConstants.isEmpty()) {
                        getCursor().putMessage(deprecation.info.source.type.name(), enumConstants);
                    }
                    break;

                case CONSTRUCTOR:
                case ENUM_METHOD:
                case INTERFACE_METHOD:
                    break;

                default:
                    throw new IllegalStateException("Unexpected value: " + deprecation.info.source.type);
            }

            return super.visitCompilationUnit(compilationUnit, context);
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext context) {
            J.Identifier id = super.visitIdentifier(identifier, context);
            Set<NameTree> values = nameTreeFromFindTypes();
            if (values.contains(id)) {
                id = (J.Identifier) maybeAddMarker(id);
            }
            return id;
        }

        @Override
        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext context) {
            J.FieldAccess fa = super.visitFieldAccess(fieldAccess, context);
            Set<NameTree> values = nameTreeFromFindTypes();
            if (values.contains(fa)) {
                fa = (J.FieldAccess) maybeAddMarker(fa);
            }
            return fa;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
            J.MethodInvocation m = super.visitMethodInvocation(method, context);
            Set<J> values = jFromFindMethods();
            if (values.contains(m)) {
                m = (J.MethodInvocation) maybeAddMarker(m);
            }
            return m;
        }

        @SuppressWarnings("unchecked")
        private Set<J> jFromFindMethods() {
            Object object = getCursor().getNearestMessage(deprecation.info.source.type.name());
            if (object instanceof Set) {
                return (Set<J>) object;
            }
            return new HashSet<>();
        }

        @SuppressWarnings("unchecked")
        private Set<NameTree> nameTreeFromFindTypes() {
            Object object = getCursor().getNearestMessage(deprecation.info.source.type.name());
            if (object instanceof Set) {
                return (Set<NameTree>) object;
            }
            return new HashSet<>();
        }

        private J maybeAddMarker(J j) {
            if (!j.getMarkers().findFirst(Deprecation.Info.class).isPresent()) {
                return j.withMarkers(j.getMarkers().addIfAbsent(deprecation.info));
            }
            return j;
        }

    }

    @AllArgsConstructor
    private static class Deprecation {
        String searchPattern;
        Deprecation.Info info;

        @AllArgsConstructor
        public static class Source {
            String sourceOfDeprecation;
            DeprecatedType type;
            String comment;
            String link;
        }

        public enum DeprecatedType {
            // An abstract class may need different conditions.
            CLASS("class"),
            CONSTRUCTOR("constructor"),
            CLASS_METHOD("classMethod"),
            ENUM("enum"),
            ENUM_CONSTANT("enumConstant"),
            ENUM_METHOD("enumMethod"),
            EXCEPTION("exception"),
            FIELD_CONSTANT("fieldConstant"),
            INTERFACE("interface"),
            INTERFACE_METHOD("interfaceMethod"),
            NONE("notProvided");

            public final String value;

            DeprecatedType(String value) {
                this.value = value;
            }

            public String getValue() {
                return value;
            }

            static final Map<String, DeprecatedType> values = Arrays.stream(DeprecatedType.values())
                    .collect(Collectors.toMap(DeprecatedType::getValue, Function.identity()));

            public static DeprecatedType fromValue(String value) {
                return values.getOrDefault(value, DeprecatedType.NONE);
            }
        }

        @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
        @Value
        public static class Info implements SearchResult {
            UUID id;
            Source source;

            @Override
            public <P> String print(TreePrinter<P> printer, P p) {
                String comment = StringUtils.isBlank(source.comment) ? "" : " Reason: " + source.comment + ".";
                String link = StringUtils.isBlank(source.link) ? "" : " Link: " + source.link + ".";
                return "/*~~> Source of " + source.type.getValue() + " deprecation: " + source.sourceOfDeprecation + "." + comment + link + " ~~*/";
            }

            @Override
            public @Nullable String getDescription() {
                return null;
            }
        }
    }

    private static Set<NameTree> findConstants(J j, @Nullable JavaType.FullyQualified fullyQualifiedType) {
        JavaIsoVisitor<Set<NameTree>> findVisitor = new JavaIsoVisitor<Set<NameTree>>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Set<NameTree> ns) {
                return super.visitClassDeclaration(classDecl, ns);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Set<NameTree> ns) {
                J.Identifier id = super.visitIdentifier(identifier, ns);
                if (isTargetClass() && isTargetFieldType(id) && fullyQualifiedType != null && id.getSimpleName()
                        .equals(fullyQualifiedType.getClassName().substring(fullyQualifiedType.getClassName().lastIndexOf(".") + 1))) {
                    ns.add(id);
                }
                return id;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, Set<NameTree> ns) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ns);
                if (fullyQualifiedType != null) {
                    JavaType.FullyQualified declaringType = JavaType.Class.build(fullyQualifiedType.getFullyQualifiedName()
                            .substring(0, fullyQualifiedType.getFullyQualifiedName().lastIndexOf(".")));

                    if (isTargetClass() && TypeUtils.isOfType(declaringType, fa.getTarget().getType()) &&
                            fa.getName().getSimpleName().equals(fullyQualifiedType.getClassName()
                                    .substring(fullyQualifiedType.getClassName().lastIndexOf(".") + 1))) {
                        ns.add(fa);
                    }
                }
                return fa;
            }

            private boolean isTargetClass() {
                Cursor parentCursor = getCursor().dropParentUntil(
                        is -> is instanceof J.CompilationUnit ||
                              is instanceof J.ClassDeclaration);
                return (parentCursor.getValue() instanceof J.ClassDeclaration && fullyQualifiedType != null &&
                        !((J.ClassDeclaration) parentCursor.getValue()).getName().getSimpleName().equals(fullyQualifiedType.getClassName()));
            }

            private boolean isTargetFieldType(J.Identifier identifier) {
                if (fullyQualifiedType != null && identifier.getFieldType() != null && identifier.getFieldType() instanceof JavaType.Variable) {
                    JavaType.Variable fieldType = ((JavaType.Variable)identifier.getFieldType());
                    JavaType.FullyQualified declaringType = JavaType.Class.build(
                            fullyQualifiedType.getFullyQualifiedName().substring(0, fullyQualifiedType.getFullyQualifiedName().lastIndexOf(".")));
                    return TypeUtils.isOfType(declaringType, fieldType.getType());
                }
                return false;
            }
        };

        Set<NameTree> ts = new HashSet<>();
        if (fullyQualifiedType != null) {
            findVisitor.visit(j, ts);
        }
        return ts;
    }
}

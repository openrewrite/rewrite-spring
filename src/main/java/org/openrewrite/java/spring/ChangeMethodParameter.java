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
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

/**
 * A recipe to change parameter type for a method declaration.
 * <p>
 * NOTE: This recipe is usually used for initial modification of parameter changes.
 * After modifying method parameters using this recipe, you may also need to modify
 * the method definition as needed to avoid compilation errors.
 */
@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeMethodParameter extends Recipe {

    /**
     * A method pattern that is used to find matching method declarations.
     * See {@link  MethodMatcher} for details on the expression's syntax.
     */
    @Option(displayName = "Method pattern",
            description = "A method pattern that is used to find the method declarations to modify.",
            example = "com.yourorg.A foo(int, int)")
    String methodPattern;

    @Option(displayName = "Parameter type",
            description = "The new type of the parameter that gets updated.",
            example = "java.lang.String")
    String parameterType;

    @Option(displayName = "Parameter index",
            description = "A zero-based index that indicates the position at which the parameter will be added.",
            example = "0")
    Integer parameterIndex;

    @Override
    public String getInstanceNameSuffix() {
        return String.format("`%s` in methods `%s`", parameterType, methodPattern);
    }

    @Override
    public String getDisplayName() {
        return "Change parameter type for a method declaration";
    }

    @Override
    public String getDescription() {
        return "Change parameter type for a method declaration, identified by a method pattern.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher methodMatcher = new MethodMatcher(methodPattern, true);
        return Preconditions.check(new DeclaresMethod<>(methodMatcher), new ChangeMethodArgumentVisitor(methodMatcher));
    }

    @RequiredArgsConstructor
    private class ChangeMethodArgumentVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher methodMatcher;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);

            if (methodMatcher.matches(md.getMethodType())) {
                if (md.getParameters().isEmpty() ||
                    md.getParameters().get(0) instanceof J.Empty ||
                    md.getParameters().size() <= parameterIndex) {
                    return md;
                }
                if (md.getParameters().get(parameterIndex) instanceof J.VariableDeclarations) {
                    J.VariableDeclarations parameter = (J.VariableDeclarations) md.getParameters().get(parameterIndex);

                    TypeTree typeTree = createTypeTree(parameterType);
                    if (TypeUtils.isOfType(parameter.getType(), typeTree.getType())) {
                        return md;
                    }

                    String parameterName = parameter.getVariables().get(0).getSimpleName();
                    parameter = parameter.withTypeExpression(typeTree).withVariables(singletonList(
                            new J.VariableDeclarations.NamedVariable(
                                    randomId(),
                                    Space.EMPTY,
                                    Markers.EMPTY,
                                    new J.Identifier(
                                            randomId(),
                                            Space.EMPTY,
                                            Markers.EMPTY,
                                            emptyList(),
                                            parameterName,
                                            typeTree.getType(),
                                            new JavaType.Variable(
                                                    null,
                                                    0,
                                                    parameterName,
                                                    md.getMethodType(),
                                                    typeTree.getType(),
                                                    null
                                            )
                                    ),
                                    emptyList(),
                                    null,
                                    null
                            )
                    ));

                    md = autoFormat(changeParameter(md, parameter), parameter, ctx, getCursor().getParentTreeCursor());
                }

            }
            return md;
        }

        private J.MethodDeclaration changeParameter(J.MethodDeclaration method, J.VariableDeclarations parameter) {
            List<Statement> originalParameters = method.getParameters();
            List<Statement> newParameters = new ArrayList<>();
            for (int i = 0; i < originalParameters.size(); i++) {
                if (i == parameterIndex) {
                    newParameters.add(parameter);
                } else {
                    newParameters.add(originalParameters.get(i));
                }
            }

            method = method.withParameters(newParameters);

            if (parameter.getTypeExpression() != null && !(parameter.getTypeExpression() instanceof J.Identifier || parameter.getTypeExpression() instanceof J.Primitive)) {
                doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(parameter.getTypeExpression()));
            }
            return method;
        }

        private TypeTree createTypeTree(String typeName) {
            int arrayIndex = typeName.lastIndexOf('[');
            if (arrayIndex != -1) {
                TypeTree elementType = createTypeTree(typeName.substring(0, arrayIndex));
                return new J.ArrayType(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        elementType,
                        null,
                        JLeftPadded.build(Space.EMPTY),
                        new JavaType.Array(null, elementType.getType(), null)
                );
            }
            int genericsIndex = typeName.indexOf('<');
            if (genericsIndex != -1) {
                TypeTree rawType = createTypeTree(typeName.substring(0, genericsIndex));
                List<JRightPadded<Expression>> typeParameters = new ArrayList<>();
                for (String typeParam : typeName.substring(genericsIndex + 1, typeName.lastIndexOf('>')).split(",")) {
                    typeParameters.add(JRightPadded.build((Expression) createTypeTree(typeParam.trim())));
                }
                return new J.ParameterizedType(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        rawType,
                        JContainer.build(Space.EMPTY, typeParameters, Markers.EMPTY),
                        new JavaType.Parameterized(null, (JavaType.FullyQualified) rawType.getType(), null)
                );
            }
            JavaType.Primitive type = JavaType.Primitive.fromKeyword(typeName);
            if (type != null) {
                return new J.Primitive(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        type
                );
            }
            if (typeName.equals("?")) {
                return new J.Wildcard(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        null,
                        null
                );
            }
            if (typeName.startsWith("?") && typeName.contains("extends")) {
                return new J.Wildcard(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        new JLeftPadded<>(Space.SINGLE_SPACE, J.Wildcard.Bound.Extends, Markers.EMPTY),
                        createTypeTree(typeName.substring(typeName.indexOf("extends") + "extends".length() + 1).trim()).withPrefix(Space.SINGLE_SPACE)
                );
            }
            if (typeName.indexOf('.') == -1) {
                String javaLangType = TypeUtils.findQualifiedJavaLangTypeName(typeName);
                if (javaLangType != null) {
                    return new J.Identifier(
                            randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            emptyList(),
                            typeName,
                            JavaType.buildType(javaLangType),
                            null
                    );
                }
            }
            TypeTree typeTree = TypeTree.build(typeName);
            // somehow the type attribution is incomplete, but `ChangeType` relies on this
            if (typeTree instanceof J.FieldAccess) {
                typeTree = ((J.FieldAccess) typeTree).withName(((J.FieldAccess) typeTree).getName().withType(typeTree.getType()));
            } else if (typeTree.getType() == null) {
                typeTree = ((J.Identifier) typeTree).withType(JavaType.ShallowClass.build(typeName));
            }
            return typeTree;
        }
    }
}

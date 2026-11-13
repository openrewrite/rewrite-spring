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
package org.openrewrite.java.spring.doc;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static org.openrewrite.java.spring.internal.MethodDeclarations.withReturnTypeExpression;

public class SecurityContextToSecurityScheme extends Recipe {
    private static final String AUTHORIZATION_SCOPE = "springfox.documentation.service.AuthorizationScope";
    private static final String SCOPES = "io.swagger.v3.oas.models.security.Scopes";
    private static final JavaType.FullyQualified SCOPES_TYPE = JavaType.ShallowClass.build(SCOPES);

    private static final MethodMatcher APIKEY_MATCHER = new MethodMatcher("springfox.documentation.service.ApiKey <constructor>(String, String, String)");
    private static final MethodMatcher AUTHORIZATION_SCOPE_MATCHER = new MethodMatcher("springfox.documentation.service.AuthorizationScope <constructor>(String, String)");
    private static final MethodMatcher SECURITY_REFERENCE_MATCHER = new MethodMatcher("springfox.documentation.service.SecurityReference <constructor>(String, springfox.documentation.service.AuthorizationScope[])");

    @Getter
    final String displayName = "Replace elements of SpringFox's security with Swagger's security models";

    @Getter
    final String description = "Replace `ApiKey`, `AuthorizationScope`, and `SecurityScheme` elements with Swagger's equivalents.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new TreeVisitor<Tree, ExecutionContext>() {

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof JavaSourceFile;
            }

            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                Tree t = replaceApiKey(ctx, tree);
                // Retypes variable references first, as the return type conversion looks at the types a body returns
                t = replaceScopeArraysAndLists(ctx, t);
                t = replaceAuthorizationScope(ctx, t);
                return replaceSecurityReference(ctx, t);

            }

            private Tree replaceApiKey(ExecutionContext ctx, Tree t) {
                return Preconditions.check(new UsesMethod<>(APIKEY_MATCHER), new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        String inValue = APIKEY_MATCHER.matches(newClass) ? passAsToSecuritySchemeIn(newClass.getArguments().get(2)) : null;
                        if (inValue != null) {
                            maybeRemoveImport("springfox.documentation.service.ApiKey");
                            maybeAddImport("io.swagger.v3.oas.models.security.SecurityScheme");
                            return JavaTemplate.builder("new SecurityScheme()\n.type(SecurityScheme.Type.APIKEY)\n.name(#{any(String)})\n.in(" + inValue + ")")
                                    .imports("io.swagger.v3.oas.models.security.SecurityScheme")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                    .build()
                                    .apply(getCursor(), newClass.getCoordinates().replace(), newClass.getArguments().get(1));
                        }
                        return super.visitNewClass(newClass, ctx);
                    }

                    private @Nullable String passAsToSecuritySchemeIn(Expression passAsExpr) {
                        if (passAsExpr instanceof J.Literal) {
                            Object passAs = ((J.Literal) passAsExpr).getValue();
                            if ("header".equals(passAs)) {
                                return "SecurityScheme.In.HEADER";
                            }
                            if ("cookie".equals(passAs)) {
                                return "SecurityScheme.In.COOKIE";
                            }
                            if ("query".equals(passAs)) {
                                return "SecurityScheme.In.QUERY";
                            }
                        }
                        return null;
                    }
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }

            private Tree replaceAuthorizationScope(ExecutionContext ctx, Tree t) {
                // Return types follow variables converted to Scopes, even where no AuthorizationScope is constructed
                TreeVisitor<?, ExecutionContext> precondition = Preconditions.or(
                        new UsesMethod<>(AUTHORIZATION_SCOPE_MATCHER),
                        findArrayParametersOfType(AUTHORIZATION_SCOPE));
                JavaVisitor<ExecutionContext> visitor = new JavaVisitor<ExecutionContext>() {
                    private final List<JavaType.Method> convertedMethods = new ArrayList<>();
                    private final MethodMatcher LIST_OF = new MethodMatcher("java.util.List of(springfox.documentation.service.AuthorizationScope...)");
                    private final MethodMatcher ARRAYS_AS_LIST = new MethodMatcher("java.util.Arrays asList(springfox.documentation.service.AuthorizationScope...)");

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                        // Check if this is List.of(...) or Arrays.asList(...) with AuthorizationScope constructors
                        if (LIST_OF.matches(mi) || ARRAYS_AS_LIST.matches(mi)) {
                            List<ScopeInfo> scopes = extractAuthorizationScopes(mi.getArguments());
                            if (!scopes.isEmpty()) {
                                return replaceWithChainedScopes(mi, scopes, ctx);
                            }
                        }
                        for (JavaType.Method converted : convertedMethods) {
                            if (TypeUtils.isOfType(mi.getMethodType(), converted)) {
                                JavaType.Method methodType = converted.withReturnType(SCOPES_TYPE);
                                return mi.withMethodType(methodType).withName(mi.getName().withType(methodType));
                            }
                        }
                        return mi;
                    }

                    @Override
                    public J visitNewArray(J.NewArray newArray, ExecutionContext ctx) {
                        J.NewArray na = (J.NewArray) super.visitNewArray(newArray, ctx);

                        // Check if this array is part of a SecurityReference constructor - if so, skip conversion
                        if (isPartOfSecurityReference(getCursor())) {
                            return na;
                        }

                        // Check if this is an array initializer with AuthorizationScope constructors
                        if (na.getInitializer() != null) {
                            List<ScopeInfo> scopes = extractAuthorizationScopes(na.getInitializer());
                            if (!scopes.isEmpty()) {
                                return replaceWithChainedScopes(na, scopes, ctx);
                            }
                        }
                        return na;
                    }

                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);

                        // A body returning a list or an array compiles only with the declared return type
                        if (md.getReturnTypeExpression() != null &&
                                isAuthorizationScopeListOrArray(md.getReturnTypeExpression().getType()) &&
                                returnsOnlyScopes(md)) {
                            if (md.getMethodType() != null) {
                                convertedMethods.add(md.getMethodType());
                            }
                            maybeRemoveImport("java.util.List");
                            maybeRemoveImport(AUTHORIZATION_SCOPE);
                            maybeAddImport(SCOPES);
                            return withReturnTypeExpression(md, TypeTree.build("Scopes")
                                    .withType(SCOPES_TYPE)
                                    .withPrefix(md.getReturnTypeExpression().getPrefix()));
                        }
                        return md;
                    }

                    private boolean returnsOnlyScopes(J.MethodDeclaration md) {
                        return md.getBody() == null || new JavaIsoVisitor<AtomicBoolean>() {
                            @Override
                            public J.Return visitReturn(J.Return return_, AtomicBoolean onlyScopes) {
                                if (return_.getExpression() == null || !isScopesOrNull(return_.getExpression())) {
                                    onlyScopes.set(false);
                                }
                                return return_;
                            }

                            private boolean isScopesOrNull(Expression expression) {
                                Expression e = expression.unwrap();
                                if (e instanceof J.Ternary) {
                                    // A ternary keeps the type it was parsed with when its branches are replaced
                                    return isScopesOrNull(((J.Ternary) e).getTruePart()) && isScopesOrNull(((J.Ternary) e).getFalsePart());
                                }
                                return e.getType() == JavaType.Primitive.Null || TypeUtils.isOfClassType(e.getType(), SCOPES);
                            }

                            @Override
                            public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean onlyScopes) {
                                return lambda;
                            }

                            @Override
                            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean onlyScopes) {
                                return newClass;
                            }

                            @Override
                            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean onlyScopes) {
                                return classDecl;
                            }
                        }.reduce(md.getBody(), new AtomicBoolean(true)).get();
                    }

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        // Handle single AuthorizationScope (not in a collection)
                        if (AUTHORIZATION_SCOPE_MATCHER.matches(newClass) && !isPartOfCollection(getCursor())) {
                            maybeRemoveImport(AUTHORIZATION_SCOPE);
                            maybeAddImport(SCOPES);
                            return JavaTemplate.builder("new Scopes().addString(#{any(String)}, #{any(String)})")
                                    .imports(SCOPES)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                    .build()
                                    .apply(getCursor(), newClass.getCoordinates().replace(),
                                            newClass.getArguments().get(0), newClass.getArguments().get(1));
                        }
                        return super.visitNewClass(newClass, ctx);
                    }

                    private boolean isPartOfCollection(Cursor cursor) {
                        // Check if this AuthorizationScope is part of a List.of, Arrays.asList, or array initializer
                        // that is replaced as a whole
                        J parentTree = cursor.getParentTreeCursor().getValue();
                        List<Expression> elements = null;
                        if (parentTree instanceof J.NewArray) {
                            elements = ((J.NewArray) parentTree).getInitializer();
                        } else if (parentTree instanceof J.MethodInvocation &&
                                (LIST_OF.matches((J.MethodInvocation) parentTree) ||
                                        ARRAYS_AS_LIST.matches((J.MethodInvocation) parentTree))) {
                            elements = ((J.MethodInvocation) parentTree).getArguments();
                        }
                        return elements != null && !extractAuthorizationScopes(elements).isEmpty();
                    }

                    private boolean isPartOfSecurityReference(Cursor cursor) {
                        // Check if this array is an argument to a SecurityReference constructor
                        Cursor parent = cursor.getParentTreeCursor();
                        if (parent.getValue() instanceof J.NewClass) {
                            J.NewClass newClass = parent.getValue();
                            return SECURITY_REFERENCE_MATCHER.matches(newClass);
                        }
                        return false;
                    }

                    // Empty unless every element is an AuthorizationScope constructor call, as a chain of
                    // addString calls has no place for any other element
                    private List<ScopeInfo> extractAuthorizationScopes(List<Expression> expressions) {
                        List<ScopeInfo> scopes = new ArrayList<>();
                        for (Expression expr : expressions) {
                            if (!(expr instanceof J.NewClass) || !AUTHORIZATION_SCOPE_MATCHER.matches((J.NewClass) expr)) {
                                return emptyList();
                            }
                            List<Expression> arguments = ((J.NewClass) expr).getArguments();
                            scopes.add(new ScopeInfo(arguments.get(0), arguments.get(1)));
                        }
                        return scopes;
                    }

                    private Expression replaceWithChainedScopes(Expression node, List<ScopeInfo> scopes, ExecutionContext ctx) {
                        maybeRemoveImport(AUTHORIZATION_SCOPE);
                        maybeRemoveImport("java.util.Arrays");
                        maybeRemoveImport("java.util.List");
                        maybeAddImport(SCOPES);

                        StringBuilder templateBuilder = new StringBuilder("new Scopes()");
                        List<Expression> templateArgs = new ArrayList<>();

                        for (ScopeInfo scope : scopes) {
                            templateBuilder.append("\n.addString(#{any(String)}, #{any(String)})");
                            templateArgs.add(scope.name);
                            templateArgs.add(scope.description);
                        }

                        return JavaTemplate.builder(templateBuilder.toString())
                                .imports(SCOPES)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                .build()
                                .apply(getCursor(), node.getCoordinates().replace(), templateArgs.toArray());
                    }

                    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
                    class ScopeInfo {
                        final Expression name;
                        final Expression description;
                    }

                };
                Tree before = t;
                Tree after = Preconditions.check(precondition, visitor).visitNonNull(t, ctx, getCursor().getParentOrThrow());
                // A method returning a call to a converted method qualifies once that call is retyped
                while (after != before) {
                    before = after;
                    after = visitor.visitNonNull(before, ctx, getCursor().getParentOrThrow());
                }
                return after;
            }

            private Tree replaceSecurityReference(ExecutionContext ctx, Tree t) {
                return Preconditions.check(new UsesMethod<>(SECURITY_REFERENCE_MATCHER), new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        if (SECURITY_REFERENCE_MATCHER.matches(newClass) && newClass.getArguments().size() == 2) {
                            Expression referenceName = newClass.getArguments().get(0);
                            Expression scopesArg = newClass.getArguments().get(1);

                            // Check if scopesArg is an inline array or a variable reference
                            if (scopesArg instanceof J.NewArray) {
                                // Case 1: Inline array - extract scope names from the AuthorizationScope array
                                List<Expression> scopeNames = extractScopeNames((J.NewArray) scopesArg);

                                if (!scopeNames.isEmpty()) {
                                    maybeRemoveImport("springfox.documentation.service.SecurityReference");
                                    maybeRemoveImport(AUTHORIZATION_SCOPE);
                                    maybeAddImport("io.swagger.v3.oas.models.security.SecurityRequirement");
                                    maybeAddImport("java.util.Arrays");

                                    // Build template: new SecurityRequirement().addList("reference", Arrays.asList("scope1", "scope2"))
                                    StringBuilder templateBuilder = new StringBuilder("new SecurityRequirement().addList(#{any(String)}, Arrays.asList(");
                                    List<Expression> templateArgs = new ArrayList<>();
                                    templateArgs.add(referenceName);

                                    for (int i = 0; i < scopeNames.size(); i++) {
                                        if (i > 0) {
                                            templateBuilder.append(", ");
                                        }
                                        templateBuilder.append("#{any(String)}");
                                        templateArgs.add(scopeNames.get(i));
                                    }
                                    templateBuilder.append("))");

                                    return JavaTemplate.builder(templateBuilder.toString())
                                            .imports("io.swagger.v3.oas.models.security.SecurityRequirement", "java.util.Arrays")
                                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                            .build()
                                            .apply(getCursor(), newClass.getCoordinates().replace(), templateArgs.toArray());
                                }
                            } else {
                                // Case 2: Any other expression - use keySet(), as declarations of this type become Scopes
                                maybeRemoveImport("springfox.documentation.service.SecurityReference");
                                maybeRemoveImport(AUTHORIZATION_SCOPE);
                                maybeAddImport("io.swagger.v3.oas.models.security.SecurityRequirement");
                                maybeAddImport("java.util.stream.Collectors");
                                return JavaTemplate.builder("new SecurityRequirement().addList(#{any(String)}, #{any(" + SCOPES + ")}.keySet().stream().collect(Collectors.toList()))")
                                        .imports("io.swagger.v3.oas.models.security.SecurityRequirement", "java.util.stream.Collectors")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                        .build()
                                        .apply(getCursor(), newClass.getCoordinates().replace(), referenceName, scopesArg);
                            }
                        }
                        return super.visitNewClass(newClass, ctx);
                    }

                    private List<Expression> extractScopeNames(J.NewArray newArray) {
                        List<Expression> scopeNames = new ArrayList<>();

                        if (newArray.getInitializer() != null) {
                            for (Expression expr : newArray.getInitializer()) {
                                if (expr instanceof J.NewClass) {
                                    J.NewClass nc = (J.NewClass) expr;
                                    if (AUTHORIZATION_SCOPE_MATCHER.matches(nc) && !nc.getArguments().isEmpty()) {
                                        // Extract just the scope name (first argument)
                                        scopeNames.add(nc.getArguments().get(0));
                                    }
                                }
                            }
                        }

                        return scopeNames;
                    }
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }

            private Tree replaceScopeArraysAndLists(ExecutionContext ctx, Tree t) {
                return Preconditions.check(findArrayParametersOfType(AUTHORIZATION_SCOPE), new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitVariableDeclarations(J.VariableDeclarations declarations, ExecutionContext ctx) {
                        J.VariableDeclarations decls = (J.VariableDeclarations) super.visitVariableDeclarations(declarations, ctx);
                        if (decls.getTypeExpression() == null || !isAuthorizationScopeListOrArray(decls.getType())) {
                            return decls;
                        }
                        maybeRemoveImport("java.util.List");
                        maybeRemoveImport(AUTHORIZATION_SCOPE);
                        maybeAddImport(SCOPES);
                        return decls
                                .withTypeExpression(TypeTree.build("Scopes")
                                        .withType(SCOPES_TYPE)
                                        .withPrefix(decls.getTypeExpression().getPrefix()))
                                .withVariables(ListUtils.map(decls.getVariables(), v -> v.getVariableType() == null ? v :
                                        v.withVariableType(v.getVariableType().withType(SCOPES_TYPE))));
                    }

                    @Override
                    public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                        JavaType.Variable fieldType = identifier.getFieldType();
                        if (fieldType != null && isAuthorizationScopeListOrArray(fieldType.getType())) {
                            return identifier.withType(SCOPES_TYPE).withFieldType(fieldType.withType(SCOPES_TYPE));
                        }
                        return identifier;
                    }

                    @Override
                    public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                        J.FieldAccess fa = (J.FieldAccess) super.visitFieldAccess(fieldAccess, ctx);
                        return fa.getName() == fieldAccess.getName() ? fa : fa.withType(fa.getName().getType());
                    }
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }

            private boolean isAuthorizationScopeListOrArray(@Nullable JavaType type) {
                if (type instanceof JavaType.Array) {
                    return TypeUtils.isOfClassType(((JavaType.Array) type).getElemType(), AUTHORIZATION_SCOPE);
                }
                if (type instanceof JavaType.Parameterized) {
                    JavaType.Parameterized parameterized = (JavaType.Parameterized) type;
                    return TypeUtils.isOfClassType(parameterized, "java.util.List") &&
                            parameterized.getTypeParameters().size() == 1 &&
                            TypeUtils.isOfClassType(parameterized.getTypeParameters().get(0), AUTHORIZATION_SCOPE);
                }
                return false;
            }

            private TreeVisitor<J, ExecutionContext> findArrayParametersOfType(String elementType) {
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J preVisit(J tree, ExecutionContext ctx) {
                        stopAfterPreVisit();
                        if (tree instanceof JavaSourceFile) {
                            for (JavaType.Method method : ((JavaSourceFile) tree).getTypesInUse().getDeclaredMethods()) {
                                if (method.getParameterTypes().stream().anyMatch(this::matches)) {
                                    return SearchResult.found(tree);
                                }
                            }
                        }
                        return tree;
                    }

                    private boolean matches(JavaType type) {
                        if (type instanceof JavaType.Array) {
                            JavaType.Array arrayType = (JavaType.Array) type;
                            return TypeUtils.isOfClassType(arrayType.getElemType(), elementType);
                        }
                        return false;
                    }
                };
            }
        };
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                new ChangeType(
                        "springfox.documentation.service.ApiKey",
                        "io.swagger.v3.oas.models.security.SecurityScheme",
                        true),
                new ChangeType(
                        "springfox.documentation.service.SecurityScheme",
                        "io.swagger.v3.oas.models.security.SecurityScheme",
                        true),
                new ChangeType(
                        "springfox.documentation.service.SecurityReference",
                        "io.swagger.v3.oas.models.security.SecurityRequirement",
                        true),
                new ChangeType(
                        "springfox.documentation.service.AuthorizationScope",
                        SCOPES,
                        true)
        );
    }
}

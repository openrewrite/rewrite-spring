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
package org.openrewrite.java.springdoc;

import org.openrewrite.*;
import org.openrewrite.analysis.constantfold.ConstantFold;
import org.openrewrite.analysis.util.CursorUtil;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SecurityContextToSecurityScheme extends Recipe {
    private static final MethodMatcher APIKEY_MATCHER = new MethodMatcher("springfox.documentation.service.ApiKey <constructor>(String, String, String)");
    private static final MethodMatcher AUTHORIZATION_SCOPE_MATCHER = new MethodMatcher("springfox.documentation.service.AuthorizationScope <constructor>(String, String)");
    private static final MethodMatcher SECURITY_REFERENCE_MATCHER = new MethodMatcher("springfox.documentation.service.SecurityReference <constructor>(String, springfox.documentation.service.AuthorizationScope[])");

    @Override
    public String getDisplayName() {
        return "Replace elements of SpringFox's security with Swagger's security models";
    }

    @Override
    public String getDescription() {
        return "Replace `ApiKey`, `AuthorizationScope`, and `SecurityScheme` elements with Swagger's equivalents.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new TreeVisitor<Tree, ExecutionContext>() {

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext executionContext) {
                return sourceFile instanceof JavaSourceFile;
            }

            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                Tree t = replaceApiKey(ctx, tree);
                t = replaceAuthorizationScope(ctx, t);
                t = replaceScopeArraysAndLists(ctx, t);
                return replaceSecurityReference(ctx, t);

            }

            private Tree replaceApiKey(ExecutionContext ctx, Tree t) {
                return Preconditions.check(new UsesMethod<>(APIKEY_MATCHER), new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        if (APIKEY_MATCHER.matches(newClass)) {
                            String inValue = passAsToSecuritySchemeIn(newClass.getArguments().get(2));
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

                    private String passAsToSecuritySchemeIn(Expression passAsExpr) {
                        return CursorUtil.findCursorForTree(getCursor(), passAsExpr)
                                .bind(c -> ConstantFold.findConstantLiteralValue(c, String.class))
                                .map(passAs -> {
                                    switch (passAs) {
                                        case "cookie":
                                            return "SecurityScheme.In.COOKIE";
                                        case "query":
                                            return "SecurityScheme.In.QUERY";
                                        default:
                                            return "SecurityScheme.In.HEADER";
                                    }
                                })
                                .orSome("SecurityScheme.In.HEADER");
                    }
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }

            private Tree replaceAuthorizationScope(ExecutionContext ctx, Tree t) {
                return Preconditions.check(new UsesMethod<>(AUTHORIZATION_SCOPE_MATCHER), new JavaVisitor<ExecutionContext>() {
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

                        // Handle return types
                        if (md.getReturnTypeExpression() != null) {
                            JavaType returnType = md.getReturnTypeExpression().getType();

                            // Check for List<AuthorizationScope>
                            if (returnType instanceof JavaType.Parameterized) {
                                JavaType.Parameterized pt = (JavaType.Parameterized) returnType;
                                if (TypeUtils.isOfClassType(pt, "java.util.List") &&
                                        pt.getTypeParameters().size() == 1 &&
                                        TypeUtils.isOfClassType(pt.getTypeParameters().get(0), "springfox.documentation.service.AuthorizationScope")) {

                                    maybeRemoveImport("springfox.documentation.service.AuthorizationScope");
                                    maybeAddImport("io.swagger.v3.oas.models.security.Scopes");

                                    md = md.withReturnTypeExpression(
                                            TypeTree.build("Scopes")
                                                    .withPrefix(md.getReturnTypeExpression().getPrefix()));
                                }
                            }

                            // Check for AuthorizationScope[]
                            if (returnType instanceof JavaType.Array) {
                                JavaType.Array at = (JavaType.Array) returnType;
                                if (TypeUtils.isOfClassType(at.getElemType(), "springfox.documentation.service.AuthorizationScope")) {
                                    maybeRemoveImport("springfox.documentation.service.AuthorizationScope");
                                    maybeAddImport("io.swagger.v3.oas.models.security.Scopes");

                                    md = md.withReturnTypeExpression(
                                            TypeTree.build("Scopes")
                                                    .withPrefix(md.getReturnTypeExpression().getPrefix()));
                                }
                            }
                        }
                        return md;
                    }

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        // Handle single AuthorizationScope (not in a collection)
                        if (AUTHORIZATION_SCOPE_MATCHER.matches(newClass) && !isPartOfCollection(getCursor())) {
                            maybeRemoveImport("springfox.documentation.service.AuthorizationScope");
                            maybeAddImport("io.swagger.v3.oas.models.security.Scopes");
                            return JavaTemplate.builder("new Scopes().addString(#{any(String)}, #{any(String)})")
                                    .imports("io.swagger.v3.oas.models.security.Scopes")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                    .build()
                                    .apply(getCursor(), newClass.getCoordinates().replace(),
                                            newClass.getArguments().get(0), newClass.getArguments().get(1));
                        }
                        return super.visitNewClass(newClass, ctx);
                    }

                    private boolean isPartOfCollection(Cursor cursor) {
                        // Check if this AuthorizationScope is part of a List.of, Arrays.asList, or array initializer
                        Cursor parent = cursor.getParentTreeCursor();
                        while (parent.getValue() instanceof J) {
                            J parentTree = parent.getValue();
                            if (parentTree instanceof J.MethodInvocation) {
                                J.MethodInvocation mi = (J.MethodInvocation) parentTree;
                                if (LIST_OF.matches(mi) || ARRAYS_AS_LIST.matches(mi)) {
                                    return true;
                                }
                            } else if (parentTree instanceof J.NewArray) {
                                return true;
                            }
                            parent = parent.getParentTreeCursor();
                        }
                        return false;
                    }

                    private boolean isPartOfSecurityReference(Cursor cursor) {
                        // Check if this array is an argument to a SecurityReference constructor
                        Cursor parent = cursor.getParentTreeCursor();
                        if (parent.getValue() instanceof J.NewClass) {
                            J.NewClass newClass = (J.NewClass) parent.getValue();
                            return SECURITY_REFERENCE_MATCHER.matches(newClass);
                        }
                        return false;
                    }

                    private List<ScopeInfo> extractAuthorizationScopes(List<Expression> expressions) {
                        List<ScopeInfo> scopes = new ArrayList<>();
                        for (Expression expr : expressions) {
                            if (expr instanceof J.NewClass) {
                                J.NewClass nc = (J.NewClass) expr;
                                if (AUTHORIZATION_SCOPE_MATCHER.matches(nc) && nc.getArguments().size() == 2) {
                                    scopes.add(new ScopeInfo(nc.getArguments().get(0), nc.getArguments().get(1)));
                                }
                            }
                        }
                        return scopes;
                    }

                    private Expression replaceWithChainedScopes(Expression node, List<ScopeInfo> scopes, ExecutionContext ctx) {
                        maybeRemoveImport("springfox.documentation.service.AuthorizationScope");
                        maybeAddImport("io.swagger.v3.oas.models.security.Scopes");

                        StringBuilder templateBuilder = new StringBuilder("new Scopes()");
                        List<Expression> templateArgs = new ArrayList<>();

                        for (ScopeInfo scope : scopes) {
                            templateBuilder.append("\n.addString(#{any(String)}, #{any(String)})");
                            templateArgs.add(scope.name);
                            templateArgs.add(scope.description);
                        }

                        return JavaTemplate.builder(templateBuilder.toString())
                                .imports("io.swagger.v3.oas.models.security.Scopes")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                .build()
                                .apply(getCursor(), ((Expression) node).getCoordinates().replace(), templateArgs.toArray());
                    }

                    class ScopeInfo {
                        final Expression name;
                        final Expression description;

                        ScopeInfo(Expression name, Expression description) {
                            this.name = name;
                            this.description = description;
                        }
                    }

                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
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
                                List<Expression> scopeNames = extractScopeNames((J.NewArray)scopesArg);

                                if (!scopeNames.isEmpty()) {
                                    maybeRemoveImport("springfox.documentation.service.SecurityReference");
                                    maybeRemoveImport("springfox.documentation.service.AuthorizationScope");
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
                                // Case 2: Variable reference - convert to Scopes and use keySet()
                                maybeRemoveImport("springfox.documentation.service.SecurityReference");
                                maybeRemoveImport("springfox.documentation.service.AuthorizationScope");
                                maybeAddImport("io.swagger.v3.oas.models.security.SecurityRequirement");
                                maybeAddImport("java.util.stream.Collectors");
                                scopesArg = ((J.Identifier) scopesArg).withType(JavaType.buildType("io.swagger.v3.oas.models.security.Scopes"))
                                        .withFieldType(((J.Identifier) scopesArg).getFieldType().withType(JavaType.buildType("io.swagger.v3.oas.models.security.Scopes")));
                                return JavaTemplate.builder("new SecurityRequirement().addList(#{any(String)}, #{any()}.keySet().stream().collect(Collectors.toList()))")
                                        .imports("io.swagger.v3.oas.models.security.SecurityRequirement", "java.util.stream.Collectors")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                        .contextSensitive()
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
                MethodDeclArrayTypeParamMatcher<ExecutionContext> scopeArrayParam =
                        new MethodDeclArrayTypeParamMatcher<>("springfox.documentation.service.AuthorizationScope");

                return Preconditions.check(scopeArrayParam, new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitVariableDeclarations(J.VariableDeclarations declarations, ExecutionContext ctx) {
                        J.VariableDeclarations decls = (J.VariableDeclarations) super.visitVariableDeclarations(declarations, ctx);
                        JavaType varType = decls.getType();

                        // Check if type is Scopes[]
                        if (varType instanceof JavaType.Array && decls.getTypeExpression() != null) {
                            JavaType.Array arrayType = (JavaType.Array) varType;
                            if (TypeUtils.isOfClassType(arrayType.getElemType(), "springfox.documentation.service.AuthorizationScope")) {
                                // Replace Scopes[] with Scopes (both syntax and type)
                                JavaType scopesType = arrayType.getElemType();
                                maybeRemoveImport("springfox.documentation.service.AuthorizationScope");
                                maybeAddImport("springfox.documentation.service.Scopes");
                                decls = decls.withTypeExpression(
                                                TypeTree.build("Scopes")
                                                        .withPrefix(decls.getTypeExpression().getPrefix()))
                                        .withType(scopesType);
                            }
                        }

                        // Check if type is List<Scopes>
                        if (varType instanceof JavaType.Parameterized && decls.getTypeExpression() != null) {
                            JavaType.Parameterized paramType = (JavaType.Parameterized) varType;
                            if (TypeUtils.isOfClassType(paramType, "java.util.List") &&
                                    paramType.getTypeParameters().size() == 1 &&
                                    TypeUtils.isOfClassType(paramType.getTypeParameters().get(0), "springfox.documentation.service.AuthorizationScope")) {
                                // Replace List<Scopes> with Scopes (both syntax and type)
                                JavaType scopesType = paramType.getTypeParameters().get(0);
                                maybeRemoveImport("java.util.List");
                                maybeRemoveImport("springfox.documentation.service.AuthorizationScope");
                                maybeAddImport("springfox.documentation.service.Scopes");
                                decls = decls.withTypeExpression(
                                                TypeTree.build("Scopes")
                                                        .withPrefix(decls.getTypeExpression().getPrefix()))
                                        .withType(scopesType);
                            }
                        }

                        return decls;
                    }
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
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
                        "io.swagger.v3.oas.models.security.Scopes",
                        true)
        );
    }
}

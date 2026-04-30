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
package org.openrewrite.java.spring.doc;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MigrateSpringFoxSecurityConfiguration extends ScanningRecipe<MigrateSpringFoxSecurityConfiguration.Accumulator> {

    private static final String SECURITY_CONFIG = "springfox.documentation.swagger.web.SecurityConfiguration";
    private static final String SECURITY_CONFIG_BUILDER = "springfox.documentation.swagger.web.SecurityConfigurationBuilder";
    private static final String BEAN_ANNOTATION = "org.springframework.context.annotation.Bean";
    private static final AnnotationMatcher BEAN_MATCHER = new AnnotationMatcher("@" + BEAN_ANNOTATION);

    private static final Map<String, String> BUILDER_METHOD_TO_PROPERTY;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clientId", "springdoc.swagger-ui.oauth.client-id");
        m.put("clientSecret", "springdoc.swagger-ui.oauth.client-secret");
        m.put("realm", "springdoc.swagger-ui.oauth.realm");
        m.put("appName", "springdoc.swagger-ui.oauth.app-name");
        m.put("scopeSeparator", "springdoc.swagger-ui.oauth.scope-separator");
        m.put("useBasicAuthenticationWithAccessCodeGrant", "springdoc.swagger-ui.oauth.use-basic-authentication-with-access-code-grant");
        m.put("enableCsrfSupport", "springdoc.swagger-ui.csrf.enabled");
        BUILDER_METHOD_TO_PROPERTY = m;
    }

    @Getter
    final String displayName = "Migrate SpringFox `SecurityConfiguration` bean to Springdoc Swagger UI properties";

    @Getter
    final String description = "Replace `@Bean` methods that return `springfox.documentation.swagger.web.SecurityConfiguration` " +
            "with the equivalent `springdoc.swagger-ui.*` configuration properties. " +
            "Only literal builder arguments are migrated; beans with non-literal arguments or unsupported builder methods " +
            "(`apiKey`, `apiKeyName`, `apiKeyVehicle`, `additionalQueryStringParams`) are left untouched for manual review. " +
            "If no Spring application configuration file exists, the bean is left in place to avoid silently dropping configuration.";

    static class Accumulator {
        final List<PropertyAssignment> assignments = new ArrayList<>();
        boolean hasConfigFile;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (isApplicationConfigFile(tree)) {
                    acc.hasConfigFile = true;
                }
                if (tree instanceof J.CompilationUnit) {
                    new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                            Expression returnExpr = migratableReturnExpression(md);
                            if (returnExpr != null) {
                                walkBuilderChain(returnExpr, acc.assignments);
                            }
                            return md;
                        }
                    }.visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!acc.hasConfigFile || acc.assignments.isEmpty()) {
                    return tree;
                }
                if (tree instanceof J.CompilationUnit) {
                    return new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                            if (migratableReturnExpression(md) == null) {
                                return md;
                            }
                            maybeRemoveImport(SECURITY_CONFIG);
                            maybeRemoveImport(SECURITY_CONFIG_BUILDER);
                            maybeRemoveImport(BEAN_ANNOTATION);
                            return null;
                        }
                    }.visit(tree, ctx);
                }
                if (isApplicationConfigFile(tree)) {
                    Tree result = tree;
                    for (PropertyAssignment pa : acc.assignments) {
                        result = new AddSpringProperty(pa.key, pa.value, null, null).getVisitor().visitNonNull(result, ctx);
                    }
                    return result;
                }
                return tree;
            }
        };
    }

    private static boolean isApplicationConfigFile(@Nullable Tree tree) {
        if (tree instanceof Properties.File) {
            return "application.properties".equals(((Properties.File) tree).getSourcePath().getFileName().toString());
        }
        if (tree instanceof Yaml.Documents) {
            return ((Yaml.Documents) tree).getSourcePath().getFileName().toString().matches("application\\.ya?ml");
        }
        return false;
    }

    private static @Nullable Expression migratableReturnExpression(J.MethodDeclaration md) {
        if (md.getReturnTypeExpression() == null ||
                !TypeUtils.isOfClassType(md.getReturnTypeExpression().getType(), SECURITY_CONFIG) ||
                md.getBody() == null) {
            return null;
        }
        boolean isBean = false;
        for (J.Annotation a : md.getLeadingAnnotations()) {
            if (BEAN_MATCHER.matches(a)) {
                isBean = true;
                break;
            }
        }
        if (!isBean) {
            return null;
        }
        List<Statement> statements = md.getBody().getStatements();
        if (statements.size() != 1 || !(statements.get(0) instanceof J.Return)) {
            return null;
        }
        Expression returnExpr = ((J.Return) statements.get(0)).getExpression();
        if (returnExpr == null) {
            return null;
        }
        return chainIsMigratable(returnExpr) ? returnExpr : null;
    }

    private static boolean chainIsMigratable(Expression returnExpr) {
        return walkBuilderChain(returnExpr, new ArrayList<>());
    }

    private static boolean walkBuilderChain(Expression expr, List<PropertyAssignment> assignments) {
        if (!(expr instanceof J.MethodInvocation)) {
            return false;
        }
        J.MethodInvocation top = (J.MethodInvocation) expr;
        if (!"build".equals(top.getSimpleName()) || !isBuilderTypedMethod(top.getMethodType())) {
            return false;
        }

        Expression cursor = top.getSelect();
        while (cursor instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) cursor;
            if (!isBuilderTypedMethod(mi.getMethodType())) {
                return false;
            }
            if ("builder".equals(mi.getSimpleName())) {
                return true;
            }
            String property = BUILDER_METHOD_TO_PROPERTY.get(mi.getSimpleName());
            if (property == null || mi.getArguments().size() != 1) {
                return false;
            }
            String literalValue = literalValueOf(mi.getArguments().get(0));
            if (literalValue == null) {
                return false;
            }
            assignments.add(new PropertyAssignment(property, literalValue));
            cursor = mi.getSelect();
        }
        return false;
    }

    private static boolean isBuilderTypedMethod(JavaType.@Nullable Method type) {
        return type != null &&
                TypeUtils.isOfClassType(type.getDeclaringType(), SECURITY_CONFIG_BUILDER);
    }

    private static @Nullable String literalValueOf(Expression e) {
        if (!(e instanceof J.Literal)) {
            return null;
        }
        Object v = ((J.Literal) e).getValue();
        return v == null ? null : v.toString();
    }

    static class PropertyAssignment {
        final String key;
        final String value;

        PropertyAssignment(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}

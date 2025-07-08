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
package org.openrewrite.java.spring.security6;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.List;

public class UpdateRequestCache extends Recipe {

    private static final MethodMatcher CONTINUE_PARAMETER_MATCHER = new MethodMatcher(
            "org.springframework.security.web.savedrequest.HttpSessionRequestCache setMatchingRequestParameterName(java.lang.String)"
    );

    private static final MethodMatcher REQUEST_CACHE_MATCHER = new MethodMatcher(
            "org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer requestCache(org.springframework.security.web.savedrequest.RequestCache)"
    );

    @Override
    public String getDisplayName() {
        return "Keep the default RequestCache querying behavior in Spring Security 5";
    }

    @Override
    public String getDescription() {
        return "By default, Spring Security 5 queries the saved request on every request, which means that in a " +
                "typical setup, the HttpSession is queried on every request to use the RequestCache. In Spring " +
                "Security 6, the default behavior has changed, and RequestCache will only be queried for a cached " +
                "request if the HTTP parameter \"continue\" is defined. To maintain the same default behavior as " +
                "Spring Security 5, either explicitly add the HTTP parameter \"continue\" to every request or use " +
                "NullRequestCache to override the default behavior.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(REQUEST_CACHE_MATCHER), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                block = super.visitBlock(block, ctx);
                List<Statement> statements = block.getStatements();

                boolean hasContinueParameterStatement = findContinueParameterStatement(statements);
                if (hasContinueParameterStatement) {
                    return block;
                }

                for (int i = 0; i < statements.size(); i++) {
                    Statement statement = statements.get(i);
                    if (isNewHttpSessionRequestCacheStatement(statement)) {
                        statement = JavaTemplate.builder("#{any()}.setMatchingRequestParameterName(\"continue\");")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "spring-security-web-6"))
                            .imports(
                                "org.springframework.security.web.savedrequest.HttpSessionRequestCache")
                            .build()
                            .apply(
                                new Cursor(getCursor(), statement),
                                statement.getCoordinates().replace(),
                                getSelect(statement)
                            );
                        statements = ListUtils.insert(statements, statement, i + 1);

                        return block.withStatements(statements);
                    }
                }
                return block;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                            ExecutionContext ctx) {
                if (REQUEST_CACHE_MATCHER.matches(method)) {
                    Expression arg = method.getArguments().get(0);
                    if (isNewHttpSessionRequestCacheExpression(arg)) {
                        JavaTemplate template = JavaTemplate.builder("new NullRequestCache()")
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "spring-security-web-6"))
                                .imports(
                                        "org.springframework.security.web.savedrequest.NullRequestCache")
                                .build();
                        maybeAddImport("org.springframework.security.web.savedrequest.NullRequestCache");
                        maybeRemoveImport("org.springframework.security.web.savedrequest.HttpSessionRequestCache");
                        arg = template.apply(
                            new Cursor(getCursor(), arg),
                            arg.getCoordinates().replace());
                        return method.withArguments(Collections.singletonList(arg));
                    }

                    return method;
                }

                return super.visitMethodInvocation(method, ctx);
            }
        });
    }

    private static J.Identifier getSelect(Statement statement) {
        return ((J.VariableDeclarations) statement).getVariables().get(0).getName();
    }

    private static boolean isNewHttpSessionRequestCacheStatement(Statement statement) {
        if (statement instanceof J.VariableDeclarations) {
            J.VariableDeclarations mv = (J.VariableDeclarations) statement;
            if (mv.getVariables().size() == 1) {
                J.VariableDeclarations.NamedVariable v = mv.getVariables().get(0);
                return TypeUtils.isOfClassType(v.getType(),
                        "org.springframework.security.web.savedrequest.HttpSessionRequestCache") &&
                        v.getInitializer() != null && isNewHttpSessionRequestCacheExpression(v.getInitializer());
            }
        }
        return false;
    }

    private static boolean isContinueParameterStatement(Statement statement) {
        if (statement instanceof J.MethodInvocation) {
            J.MethodInvocation m = (J.MethodInvocation) statement;

            if (CONTINUE_PARAMETER_MATCHER.matches(m)) {
                if (m.getArguments().get(0) instanceof J.Literal) {
                    return "continue".equals(((J.Literal) m.getArguments().get(0)).getValue());
                }
            }
        }
        return false;
    }

    private static boolean findContinueParameterStatement(List<Statement> statements) {
        return statements.stream().anyMatch(UpdateRequestCache::isContinueParameterStatement);
    }

    private static boolean isNewHttpSessionRequestCacheExpression(Expression expression) {
        if (!(expression instanceof J.NewClass)) {
            return false;
        }
        J.NewClass newClass = (J.NewClass) expression;

        if (TypeUtils.isOfClassType(newClass.getConstructorType().getReturnType(),
                "org.springframework.security.web.savedrequest.HttpSessionRequestCache")) {
            return true;
        }
        return false;
    }
}

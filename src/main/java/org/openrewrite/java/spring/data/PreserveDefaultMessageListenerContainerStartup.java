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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;

public class PreserveDefaultMessageListenerContainerStartup extends Recipe {

    private static final String LISTENER_CONTAINER =
            "org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer";

    private static final AnnotationMatcher BEAN_MATCHER =
            new AnnotationMatcher("@org.springframework.context.annotation.Bean");

    private static final String SET_AUTO_STARTUP_PATTERN =
            LISTENER_CONTAINER + " setAutoStartup(boolean)";

    private static final String MANUAL_REVIEW_MESSAGE =
            "Unable to safely preserve DefaultMessageListenerContainer startup behavior. " +
                    "Configure setAutoStartup(false) manually.";

    @Getter
    final String displayName =
            "Preserve manual MongoDB listener container startup";

    @Getter
    final String description =
            "Preserve the Spring Data MongoDB 4.x startup behavior of Spring-managed " +
                    "`DefaultMessageListenerContainer` beans by explicitly disabling automatic startup.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(LISTENER_CONTAINER, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(
                            J.MethodDeclaration method,
                            ExecutionContext ctx) {

                        J.MethodDeclaration m =
                                super.visitMethodDeclaration(method, ctx);

                        if (!isListenerContainerBean(m) ||
                                m.getBody() == null ||
                                hasExplicitAutoStartup(m)) {
                            return m;
                        }

                        J.VariableDeclarations containerVariable =
                                findContainerVariable(m);

                        J.Return returnStatement =
                                findReturnStatement(m);

                        if (containerVariable != null &&
                                returnStatement != null &&
                                returnsVariable(
                                        returnStatement,
                                        containerVariable
                                )) {

                            return addAutoStartupFalse(
                                    m,
                                    returnStatement,
                                    ctx
                            );
                        }

                        if (returnStatement != null &&
                                isNewListenerContainer(
                                        returnStatement.getExpression()
                                )) {

                            return replaceDirectReturn(
                                    m,
                                    returnStatement,
                                    ctx
                            );
                        }

                        return markForManualReview(m);
                    }

                    private boolean isListenerContainerBean(
                            J.MethodDeclaration method) {

                        return service(AnnotationService.class)
                                .matches(getCursor(), BEAN_MATCHER) &&
                                method.getReturnTypeExpression() != null &&
                                TypeUtils.isAssignableTo(
                                        LISTENER_CONTAINER,
                                        method.getReturnTypeExpression()
                                                .getType()
                                );
                    }

                    private J.MethodDeclaration addAutoStartupFalse(
                            J.MethodDeclaration method,
                            J.Return returnStatement,
                            ExecutionContext ctx) {

                        return JavaTemplate.builder(
                                        "#{any(" +
                                                LISTENER_CONTAINER +
                                                ")}.setAutoStartup(false);"
                                )
                                .contextSensitive()
                                .javaParser(javaParser(ctx))
                                .build()
                                .apply(
                                        updateCursor(method),
                                        returnStatement.getCoordinates()
                                                .before(),
                                        returnStatement.getExpression()
                                );
                    }

                    private J.MethodDeclaration replaceDirectReturn(
                            J.MethodDeclaration method,
                            J.Return returnStatement,
                            ExecutionContext ctx) {

                        Expression expression =
                                returnStatement.getExpression();

                        String variableName =
                                VariableNameUtils.generateVariableName(
                                        "container",
                                        getCursor(),
                                        VariableNameUtils.GenerationStrategy
                                                .INCREMENT_NUMBER
                                );

                        return JavaTemplate.builder(
                                        "DefaultMessageListenerContainer " +
                                                variableName +
                                                " = #{any(" +
                                                LISTENER_CONTAINER +
                                                ")};\n" +
                                                variableName +
                                                ".setAutoStartup(false);\n" +
                                                "return " +
                                                variableName +
                                                ";"
                                )
                                .contextSensitive()
                                .imports(LISTENER_CONTAINER)
                                .javaParser(javaParser(ctx))
                                .build()
                                .apply(
                                        updateCursor(method),
                                        returnStatement.getCoordinates()
                                                .replace(),
                                        expression
                                );
                    }

                    private JavaParser.Builder<?, ?> javaParser(
                            ExecutionContext ctx) {

                        return JavaParser.fromJavaVersion()
                                .classpathFromResources(
                                        ctx,
                                        "spring-data-mongodb-5.0",
                                        "spring-context-6"
                                );
                    }
                }
        );
    }

    private static boolean hasExplicitAutoStartup(
            J.MethodDeclaration method) {

        return !FindMethods.find(
                method,
                SET_AUTO_STARTUP_PATTERN
        ).isEmpty();
    }

    private static J.VariableDeclarations findContainerVariable(
            J.MethodDeclaration method) {

        if (method.getBody() == null) {
            return null;
        }

        for (Statement statement :
                method.getBody().getStatements()) {

            if (!(statement instanceof J.VariableDeclarations)) {
                continue;
            }

            J.VariableDeclarations declarations =
                    (J.VariableDeclarations) statement;

            if (TypeUtils.isAssignableTo(
                    LISTENER_CONTAINER,
                    declarations.getType()) &&
                    declarations.getVariables().size() == 1 &&
                    isNewListenerContainer(
                            declarations.getVariables()
                                    .get(0)
                                    .getInitializer()
                    )) {

                return declarations;
            }
        }

        return null;
    }

    private static J.Return findReturnStatement(
            J.MethodDeclaration method) {

        if (method.getBody() == null) {
            return null;
        }

        List<Statement> statements =
                method.getBody().getStatements();

        for (int i = statements.size() - 1; i >= 0; i--) {
            Statement statement = statements.get(i);

            if (statement instanceof J.Return) {
                return (J.Return) statement;
            }
        }

        return null;
    }

    private static boolean returnsVariable(
            J.Return returnStatement,
            J.VariableDeclarations declarations) {

        if (!(returnStatement.getExpression()
                instanceof J.Identifier)) {
            return false;
        }

        J.Identifier returnedIdentifier =
                (J.Identifier) returnStatement.getExpression();

        return declarations.getVariables().stream()
                .map(variable ->
                        variable.getName().getSimpleName())
                .anyMatch(
                        returnedIdentifier
                                .getSimpleName()::equals
                );
    }

    private static boolean isNewListenerContainer(
            Expression expression) {

        if (!(expression instanceof J.NewClass)) {
            return false;
        }

        JavaType type =
                ((J.NewClass) expression).getType();

        return TypeUtils.isAssignableTo(
                LISTENER_CONTAINER,
                type
        );
    }

    private static J.MethodDeclaration markForManualReview(
            J.MethodDeclaration method) {

        List<J.Annotation> annotations =
                method.getLeadingAnnotations();

        return method.withLeadingAnnotations(
                org.openrewrite.internal.ListUtils.map(
                        annotations,
                        annotation ->
                                TypeUtils.isOfClassType(
                                        annotation.getType(),
                                        "org.springframework.context.annotation.Bean"
                                ) ?
                                        SearchResult.found(
                                                annotation,
                                                MANUAL_REVIEW_MESSAGE
                                        ) :
                                        annotation
                )
        );
    }
}

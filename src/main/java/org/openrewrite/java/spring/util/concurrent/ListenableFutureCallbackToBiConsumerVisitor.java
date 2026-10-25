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
package org.openrewrite.java.spring.util.concurrent;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.StringJoiner;

class ListenableFutureCallbackToBiConsumerVisitor extends JavaVisitor<ExecutionContext> {

    private static final String BI_CONSUMER = "java.util.function.BiConsumer";
    private static final String LISTENABLE_FUTURE_CALLBACK = "org.springframework.util.concurrent.ListenableFutureCallback";

    private static final MethodMatcher SUCCESS_CALLBACK_MATCHER = new MethodMatcher("org.springframework.util.concurrent.SuccessCallback onSuccess(..)", true);
    private static final MethodMatcher FAILURE_CALLBACK_MATCHER = new MethodMatcher("org.springframework.util.concurrent.FailureCallback onFailure(java.lang.Throwable)", true);

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
        if (!implementsListenableFutureCallback(cd)) {
            return cd;
        }

        J.MethodDeclaration successCallback = extract(SUCCESS_CALLBACK_MATCHER, cd.getBody());
        J.MethodDeclaration failureCallback = extract(FAILURE_CALLBACK_MATCHER, cd.getBody());
        if (successCallback == null || failureCallback == null) {
            return cd;
        }
        J.Identifier failureParam = ((J.VariableDeclarations) failureCallback.getParameters().get(0)).getVariables().get(0).getName();

        String template = String.format("class %s%s implements BiConsumer<%s, Throwable> {\n" +
                        "    @Override\n" +
                        "    public void accept(%s, %s) {\n" +
                        "        if (#{any(java.lang.Throwable)} == null) #{}\n" +
                        "        else #{}\n" +
                        "    }\n" +
                        "}",
                cd.getSimpleName(),
                typeParameters(cd, getCursor()),
                ((J.VariableDeclarations) successCallback.getParameters().get(0)).getTypeExpression(),
                successCallback.getParameters().get(0),
                failureCallback.getParameters().get(0));
        J.ClassDeclaration biConsumerClass = JavaTemplate.builder(template)
                .contextSensitive()
                .imports(BI_CONSUMER)
                .build()
                .apply(updateCursor(cd), cd.getCoordinates().replace(),
                        failureParam,
                        successCallback.getBody(),
                        failureCallback.getBody());
        //noinspection DataFlowIssue
        TypeTree biConsumer = biConsumerClass.getImplements().get(0);
        Statement accept = biConsumerClass.getBody().getStatements().get(0);

        maybeRemoveImport(LISTENABLE_FUTURE_CALLBACK);
        maybeAddImport(BI_CONSUMER);
        List<Statement> statements = ListUtils.filter(cd.getBody().getStatements(), s -> s != failureCallback);
        return cd
                .withImplements(ListUtils.map(cd.getImplements(), i ->
                        isListenableFutureCallback(i) ? biConsumer.withPrefix(i.getPrefix()) : i))
                .withBody(cd.getBody().withStatements(ListUtils.map(statements, s ->
                        s == successCallback ? accept.withPrefix(s.getPrefix()) : s)))
                .withType(replaceListenableFutureCallbackInterface(cd.getType(), biConsumer.getType()));
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
        if (!isListenableFutureCallback(nc.getClazz())) {
            return nc;
        }

        J.MethodDeclaration successCallback = extract(SUCCESS_CALLBACK_MATCHER, nc.getBody());
        J.MethodDeclaration failureCallback = extract(FAILURE_CALLBACK_MATCHER, nc.getBody());
        if (successCallback == null || failureCallback == null) {
            return nc;
        }
        J.Identifier failureParam = ((J.VariableDeclarations) failureCallback.getParameters().get(0)).getVariables().get(0).getName();

        maybeRemoveImport(LISTENABLE_FUTURE_CALLBACK);
        if (nc.getBody().getStatements().size() > 2) {
            String template = String.format("new BiConsumer<%s, Throwable>() {\n" +
                            "    @Override\n" +
                            "    public void accept(%s, %s) {\n" +
                            "        if (#{any(java.lang.Throwable)} == null) #{}\n" +
                            "        else #{}\n" +
                            "    }\n" +
                            "}",
                    ((J.VariableDeclarations) successCallback.getParameters().get(0)).getTypeExpression(),
                    successCallback.getParameters().get(0),
                    failureCallback.getParameters().get(0)
            );
            J.NewClass newClassDeclaration = JavaTemplate.builder(template)
                    .contextSensitive()
                    .imports(BI_CONSUMER)
                    .build()
                    .apply(updateCursor(nc), nc.getCoordinates().replace(),
                            failureParam,
                            successCallback.getBody(),
                            failureCallback.getBody());

            List<Statement> additionalStatements = ListUtils.filter(nc.getBody().getStatements(), s ->
                    !(s == successCallback || s == failureCallback));
            Statement acceptMethodWithPrefix = newClassDeclaration.getBody().getStatements().get(0)
                    .withPrefix(successCallback.getPrefix());
            newClassDeclaration = newClassDeclaration.withBody(nc.getBody().
                    withStatements(ListUtils.concat(additionalStatements, acceptMethodWithPrefix)));

            maybeAddImport(BI_CONSUMER);
            return newClassDeclaration.withPrefix(nc.getPrefix());
        }

        String template = String.format("(%s, %s) -> {\n" +
                        "    if (#{any(java.lang.Throwable)} == null) #{}\n" +
                        "    else #{}\n" +
                        "}",
                successCallback.getParameters().get(0),
                failureCallback.getParameters().get(0)
        );
        J.Lambda newClassDeclaration = JavaTemplate.builder(template)
                .contextSensitive()
                .imports(BI_CONSUMER)
                .build()
                .apply(updateCursor(nc), nc.getCoordinates().replace(),
                        failureParam,
                        successCallback.getBody(),
                        failureCallback.getBody());
        return newClassDeclaration.withPrefix(nc.getPrefix());
    }

    private static boolean implementsListenableFutureCallback(J.ClassDeclaration cd) {
        if (cd.getImplements() != null) {
            for (TypeTree implemented : cd.getImplements()) {
                if (isListenableFutureCallback(implemented)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isListenableFutureCallback(@Nullable TypeTree typeTree) {
        return typeTree != null && TypeUtils.isOfClassType(typeTree.getType(), LISTENABLE_FUTURE_CALLBACK);
    }

    private static String typeParameters(J.ClassDeclaration cd, Cursor cursor) {
        if (cd.getTypeParameters() == null) {
            return "";
        }
        StringJoiner typeParameters = new StringJoiner(", ", "<", ">");
        for (J.TypeParameter typeParameter : cd.getTypeParameters()) {
            typeParameters.add(typeParameter.printTrimmed(cursor));
        }
        return typeParameters.toString();
    }

    private static JavaType.@Nullable FullyQualified replaceListenableFutureCallbackInterface(
            JavaType.@Nullable FullyQualified type, @Nullable JavaType biConsumer) {
        if (!(type instanceof JavaType.Class) || !(biConsumer instanceof JavaType.FullyQualified)) {
            return type;
        }
        return ((JavaType.Class) type).withInterfaces(ListUtils.map(type.getInterfaces(), i ->
                TypeUtils.isOfClassType(i, LISTENABLE_FUTURE_CALLBACK) ? (JavaType.FullyQualified) biConsumer : i));
    }

    private static J.@Nullable MethodDeclaration extract(MethodMatcher matcher, J.@Nullable Block body) {
        if (body != null) {
            for (Statement statement : body.getStatements()) {
                if (statement instanceof J.MethodDeclaration &&
                    matcher.matches(((J.MethodDeclaration) statement).getMethodType())) {
                    return (J.MethodDeclaration) statement;
                }
            }
        }
        return null;
    }
}

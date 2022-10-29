/*
 * Copyright 2021 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Marker;

import java.util.*;

public class ReplaceDeprecatedEnvironmentTestUtils extends Recipe {

    private static final MethodMatcher APP_CONTEXT = new MethodMatcher("org.springframework.boot.test.util.EnvironmentTestUtils addEnvironment(org.springframework.context.ConfigurableApplicationContext, String...)");
    private static final MethodMatcher ENVIRONMENT = new MethodMatcher("org.springframework.boot.test.util.EnvironmentTestUtils addEnvironment(org.springframework.core.env.ConfigurableEnvironment, String...)");
    private static final MethodMatcher NAMED_ENVIRONMENT = new MethodMatcher("org.springframework.boot.test.util.EnvironmentTestUtils addEnvironment(String, org.springframework.core.env.ConfigurableEnvironment, String...)");

    @Override
    public String getDisplayName() {
        return "Replace `EnvironmentTestUtils` with `TestPropertyValues`";
    }

    @Override
    public String getDescription() {
        return "Replaces any references to the deprecated `EnvironmentTestUtils`" +
                " with `TestPropertyValues` and the appropriate functionality.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new FindEnvironmentTestUtilsVisitor();
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.boot.test.util.EnvironmentTestUtils");
    }

    private static final class ReplaceEnvironmentUtilsMarker implements Marker {
        private final String templateString;
        private final List<Expression> parameters;
        private final UUID id;

        private ReplaceEnvironmentUtilsMarker(String templateString, List<Expression> parameters, UUID id) {
            this.templateString = templateString;
            this.parameters = parameters;
            this.id = id;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ReplaceEnvironmentUtilsMarker withId(UUID id) {
            return new ReplaceEnvironmentUtilsMarker(templateString, parameters, id);
        }
    }

    private static class FindEnvironmentTestUtilsVisitor extends JavaIsoVisitor<ExecutionContext> {
        public static final int MINIMUM_ARGUMENT_COUNT = 2;
        private static final int MINIMUM_ARGUMENT_COUNT_WITH_NAME = 3;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            return super.visitClassDeclaration(classDecl, executionContext);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);

            if (m.getBody() == null || m.getBody().getStatements().isEmpty()) {
                return m;
            }

            List<Statement> statements = m.getBody().getStatements();
            List<Statement> newStatements = new ArrayList<>();
            List<J.MethodInvocation> collectedEnvironmentMethods = new ArrayList<>();
            boolean requiresRemoval = false;

            for (Statement statement : statements) {
                if (statement instanceof J.MethodInvocation && isAddEnvironmentMethod((J.MethodInvocation) statement)) {
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) statement;
                    if (collectedEnvironmentMethods.isEmpty() || isCollectedContextOrEnvironment(collectedEnvironmentMethods, methodInvocation)) {
                        collectedEnvironmentMethods.add(methodInvocation);
                        requiresRemoval = true;
                    } else {
                        newStatements.add(coalesceToFluentMethod(collectedEnvironmentMethods));
                        collectedEnvironmentMethods = new ArrayList<>();
                        collectedEnvironmentMethods.add(methodInvocation);
                    }
                } else {
                    if (!collectedEnvironmentMethods.isEmpty()) {
                        newStatements.add(coalesceToFluentMethod(collectedEnvironmentMethods));
                        collectedEnvironmentMethods = new ArrayList<>();
                    }
                    newStatements.add(statement);
                }
            }

            if (!collectedEnvironmentMethods.isEmpty()) {
                newStatements.add(coalesceToFluentMethod(collectedEnvironmentMethods));
            }

            if (requiresRemoval) {
                doAfterVisit(new ReplaceDeprecatedEnvironmentTestUtils.RemoveEnvironmentTestUtilsVisitor());
            }

            return m.withBody(m.getBody().withStatements(newStatements));
        }

        private boolean isCollectedContextOrEnvironment(List<J.MethodInvocation> collectedMethods, J.MethodInvocation methodInvocation) {
            if (methodInvocation.getArguments().isEmpty()
                    || collectedMethods.isEmpty()
                    || collectedMethods.get(0).getArguments().isEmpty()) {
                return false;
            }
            J.MethodInvocation collectedMethod = collectedMethods.get(0);
            Expression contextOrEnvironmentToCheck = getContextOrEnvironmentArgument(methodInvocation);
            Expression collectedContextOrEnvironment = getContextOrEnvironmentArgument(collectedMethod);

            Expression environmentNameToCheck = getEnvironmentNameArgument(methodInvocation);
            Expression collectedEnvironmentName = getEnvironmentNameArgument(collectedMethod);

            return !(contextOrEnvironmentToCheck instanceof J.NewClass) &&
                    SemanticallyEqual.areEqual(contextOrEnvironmentToCheck, collectedContextOrEnvironment)
                    && (environmentNameToCheck == null && collectedEnvironmentName == null)
                    || (environmentNameToCheck != null && collectedEnvironmentName != null
                    && SemanticallyEqual.areEqual(environmentNameToCheck, collectedEnvironmentName));
        }

        @Nullable
        private Expression getEnvironmentNameArgument(J.MethodInvocation methodInvocation) {
            if (methodInvocation.getArguments().size() < MINIMUM_ARGUMENT_COUNT_WITH_NAME) {
                return null;
            }
            Expression firstArgument = methodInvocation.getArguments().get(0);

            if (firstArgument.getType() != null && firstArgument.getType().equals(JavaType.Primitive.String)) {
                return firstArgument;
            }
            return null;
        }

        private List<Expression> getPairArguments(J.MethodInvocation methodInvocation) {
            if (methodInvocation.getArguments().size() < MINIMUM_ARGUMENT_COUNT) {
                throw new IllegalArgumentException("getPairArguments requires a method with at least " + MINIMUM_ARGUMENT_COUNT + " arguments");
            }
            List<Expression> pairArguments = new ArrayList<>();
            int startingIndex = isNamedEnvironmentMethod(methodInvocation) ? 2 : 1;
            for (int i = startingIndex; i < methodInvocation.getArguments().size(); i++) {
                pairArguments.add(methodInvocation.getArguments().get(i));
            }
            return pairArguments;
        }

        private Expression getContextOrEnvironmentArgument(J.MethodInvocation methodInvocation) {
            if (methodInvocation.getArguments().size() < MINIMUM_ARGUMENT_COUNT) {
                throw new IllegalArgumentException("getContextOrEnvironmentArgument requires a method with at least " + MINIMUM_ARGUMENT_COUNT + " arguments");
            }
            return methodInvocation.getArguments().get(isNamedEnvironmentMethod(methodInvocation) ? 1 : 0);
        }

        private J.MethodInvocation coalesceToFluentMethod(List<J.MethodInvocation> collectedMethods) {
            if (collectedMethods.isEmpty()) {
                throw new IllegalArgumentException("collectedMethods must have at least one element");
            }
            J.MethodInvocation toReplace = collectedMethods.get(0);

            String currentTemplateString = generateTemplateString(collectedMethods);
            List<Expression> parameters = generateParameters(collectedMethods);

            return toReplace.withMarkers(toReplace.getMarkers().addIfAbsent(new ReplaceEnvironmentUtilsMarker(currentTemplateString, parameters, UUID.randomUUID())));
        }

        private List<Expression> generateParameters(List<J.MethodInvocation> collectedMethods) {
            if (collectedMethods.isEmpty()) {
                throw new IllegalArgumentException("collectedMethods must have at least one element");
            }
            List<Expression> parameters = new ArrayList<>();
            for (J.MethodInvocation collectedMethod : collectedMethods) {
                parameters.addAll(getPairArguments(collectedMethod));
            }
            parameters.add(getContextOrEnvironmentArgument(collectedMethods.get(0)));

            if (isNamedEnvironmentMethod(collectedMethods.get(0))) {
                parameters.add(collectedMethods.get(0).getArguments().get(0));
            }

            return parameters;
        }

        private String generateTemplateString(List<J.MethodInvocation> collectedMethods) {
            StringBuilder template = new StringBuilder("TestPropertyValues");
            boolean appendOf = true;
            for (J.MethodInvocation methodInvocation : collectedMethods) {
                for (int j = isNamedEnvironmentMethod(methodInvocation) ? 2 : 1; j < methodInvocation.getArguments().size(); j++) {
                    template.append(".").append(appendOf ? "of" : "and").append("(#{any()})");
                    appendOf = false;
                }
            }
            if (isNamedEnvironmentMethod(collectedMethods.get(0))) {
                template.append(".applyTo(#{any()}, TestPropertyValues.Type.MAP, #{any()})");
            } else {
                template.append(".applyTo(#{any()})");
            }
            return template.toString();
        }

        private boolean isAddEnvironmentMethod(J.MethodInvocation method) {
            return APP_CONTEXT.matches(method) || ENVIRONMENT.matches(method) || isNamedEnvironmentMethod(method);
        }

        private boolean isNamedEnvironmentMethod(J.MethodInvocation method) {
            return NAMED_ENVIRONMENT.matches(method);
        }
    }

    private static class RemoveEnvironmentTestUtilsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
            Optional<ReplaceEnvironmentUtilsMarker> maybeMarker = m.getMarkers().findFirst(ReplaceEnvironmentUtilsMarker.class);
            if (maybeMarker.isPresent()) {
                ReplaceEnvironmentUtilsMarker marker = maybeMarker.get();
                m = m.withTemplate(
                        JavaTemplate.builder(this::getCursor, marker.templateString)
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                        .dependsOn(Collections.singletonList(Parser.Input.fromResource("/TestPropertyValues.java")))
                                        .build())
                                .imports("org.springframework.boot.test.util.TestPropertyValues")
                                .build(),
                        m.getCoordinates().replace(),
                        marker.parameters.toArray()
                );

                maybeRemoveImport("org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment");
                maybeAddImport("org.springframework.boot.test.util.TestPropertyValues");
            }
            return m;
        }
    }
}

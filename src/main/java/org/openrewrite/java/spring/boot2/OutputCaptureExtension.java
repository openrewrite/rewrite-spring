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

import lombok.SneakyThrows;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.text.RuleBasedCollator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class OutputCaptureExtension extends Recipe {
    private static final Supplier<JavaParser> JAVA_PARSER = () ->
            JavaParser.fromJavaVersion()
                    .dependsOn(Arrays.asList(
                            Parser.Input.fromString("package org.springframework.boot.test.system;\n" +
                                    "public class OutputCaptureExtension {\n" +
                                    "}"),
                            Parser.Input.fromString("package org.springframework.boot.test.system;\n" +
                                    "public interface CapturedOutput {\n" +
                                    "  String getAll();\n" +
                                    "}"
                            ),
                            Parser.Input.fromString("package org.junit.jupiter.api.extension;\n" +
                                    "public @interface ExtendWith {\n" +
                                    "  Class[] value();\n" +
                                    "}")
                    ))
                    .build();

    @Override
    public String getDisplayName() {
        return "Migrate `@OutputCaptureRule` to `@ExtendWith(OutputCaptureExtension.class)`";
    }

    @Override
    public String getDescription() {
        return "Use the JUnit Jupiter extension instead of JUnit 4 rule.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesType<>("org.springframework.boot.test.system.OutputCaptureRule"));
                doAfterVisit(new UsesType<>("org.springframework.boot.test.rule.OutputCapture"));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate addOutputCaptureExtension = JavaTemplate.builder(this::getCursor, "@ExtendWith(OutputCaptureExtension.class)")
                    .javaParser(JAVA_PARSER)
                    .imports("org.junit.jupiter.api.extension.ExtendWith",
                            "org.springframework.boot.test.system.OutputCaptureExtension")
                    .build();

            @SneakyThrows
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext context) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, context);

                c = c.withBody(c.getBody().withStatements(ListUtils.map(c.getBody().getStatements(), s -> {
                    if (!(s instanceof J.VariableDeclarations)) {
                        return s;
                    }

                    J.VariableDeclarations field = (J.VariableDeclarations) s;
                    JavaType.FullyQualified fieldType = field.getTypeAsFullyQualified();
                    if (TypeUtils.isOfClassType(fieldType, "org.springframework.boot.test.system.OutputCaptureRule") ||
                            TypeUtils.isOfClassType(fieldType, "org.springframework.boot.test.rule.OutputCapture")) {

                        String fieldName = field.getVariables().get(0).getSimpleName();

                        //Add the CapturedOutput parameter to any method that has a method call to OutputCapture
                        doAfterVisit(new AddCapturedOutputParameter(fieldName));

                        //Convert any method invocations from OutputCaptureRule.expect(Matcher) -> Matcher.match(CapturedOutput.getAll())
                        doAfterVisit(new ConvertExpectMethods(fieldName));

                        //Covert any remaining method calls from OutputCapture -> CapturedOutput
                        doAfterVisit(new ChangeMethodTargetToVariable("org.springframework.boot.test.rule.OutputCapture *(..)",
                                fieldName, "org.springframework.boot.test.system.CapturedOutput", false));
                        doAfterVisit(new ChangeMethodTargetToVariable("org.springframework.boot.test.system.OutputCaptureRule *(..)",
                                fieldName, "org.springframework.boot.test.system.CapturedOutput", false));

                        getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "addOutputCaptureExtension", true);

                        maybeRemoveImport("org.springframework.boot.test.system.OutputCaptureRule");
                        maybeRemoveImport("org.springframework.boot.test.rule.OutputCapture");
                        maybeRemoveImport("org.junit.Rule");

                        return null;
                    }
                    return s;
                })));

                if (classDecl.getBody().getStatements().size() != c.getBody().getStatements().size()) {
                    c = c.withTemplate(addOutputCaptureExtension, c.getCoordinates()
                            .addAnnotation(Comparator.comparing(
                                    J.Annotation::getSimpleName,
                                    new RuleBasedCollator("< ExtendWith")
                            ))
                    );

                    maybeAddImport("org.springframework.boot.test.system.OutputCaptureExtension");
                    maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                }

                return c;
            }
        };
    }

    private static final MethodMatcher OUTPUT_CAPTURE_MATCHER = new MethodMatcher(
            "org.springframework.boot.test.rule.OutputCapture expect(..)"
    );
    private static final MethodMatcher OUTPUT_CAPTURE_RULE_MATCHER = new MethodMatcher(
            "org.springframework.boot.test.system.OutputCaptureRule expect(..)"
    );

    private static final class ConvertExpectMethods extends JavaIsoVisitor<ExecutionContext> {
        private final JavaTemplate matchesTemplate = JavaTemplate.builder(this::getCursor, "#{any()}.matches(#{}.getAll())")
                .javaParser(JAVA_PARSER)
                .build();

        private final String variableName;

        private ConvertExpectMethods(String variableName) {
            this.variableName = variableName;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
            if (!OUTPUT_CAPTURE_MATCHER.matches(m) && !OUTPUT_CAPTURE_RULE_MATCHER.matches(m)) {
                return m;
            }
            m = m.withTemplate(matchesTemplate, m.getCoordinates().replace(), m.getArguments().get(0), variableName);
            return m;
        }
    }

    private static final class AddCapturedOutputParameter extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType.Class CAPTURED_OUTPUT_TYPE = JavaType.ShallowClass.build("org.springframework.boot.test.system.CapturedOutput");
        private final String variableName;

        private AddCapturedOutputParameter(String variableName) {
            this.variableName = variableName;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext context) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, context);
            if (!FindMethods.find(m, "org.springframework.boot.test.rule.OutputCapture *(..)").isEmpty() ||
                    !FindMethods.find(m, "org.springframework.boot.test.system.OutputCaptureRule *(..)").isEmpty()) {
                // FIXME need addParameter coordinate here...
                // m = m.withTemplate(parameter.build(), m.getCoordinates().replaceParameters());

                J.VariableDeclarations param = new J.VariableDeclarations(Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        emptyList(),
                        emptyList(),
                        new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, "CapturedOutput", CAPTURED_OUTPUT_TYPE, null),
                        null,
                        emptyList(),
                        singletonList(new JRightPadded<>(
                                new J.VariableDeclarations.NamedVariable(Tree.randomId(),
                                        Space.format(" "),
                                        Markers.EMPTY,
                                        new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, variableName, CAPTURED_OUTPUT_TYPE, null),
                                        emptyList(),
                                        null,
                                        null),
                                Space.EMPTY,
                                Markers.EMPTY
                        ))
                );

                if (m.getParameters().iterator().next() instanceof J.Empty) {
                    m = m.withParameters(singletonList(param));
                } else {
                    m = m.withParameters(ListUtils.concat(m.getParameters(), param.withPrefix(Space.format(" "))));
                }

                maybeAddImport("org.springframework.boot.test.system.CapturedOutput");
            }
            return m;
        }
    }
}

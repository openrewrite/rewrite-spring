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
package org.openrewrite.java.spring.boot2;

import lombok.SneakyThrows;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.text.RuleBasedCollator;
import java.util.Comparator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class OutputCaptureExtension extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate `@OutputCaptureRule` to `@ExtendWith(OutputCaptureExtension.class)`";
    }

    @Override
    public String getDescription() {
        return "Use the JUnit Jupiter extension instead of JUnit 4 rule.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesType<>("org.springframework.boot.test.system.OutputCaptureRule", false),
                new UsesType<>("org.springframework.boot.test.rule.OutputCapture", false)
        ), new JavaIsoVisitor<ExecutionContext>() {

            @SneakyThrows
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

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
                                fieldName, "org.springframework.boot.test.system.CapturedOutput", false).getVisitor());
                        doAfterVisit(new ChangeMethodTargetToVariable("org.springframework.boot.test.system.OutputCaptureRule *(..)",
                                fieldName, "org.springframework.boot.test.system.CapturedOutput", false).getVisitor());

                        getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "addOutputCaptureExtension", true);

                        maybeRemoveImport("org.springframework.boot.test.system.OutputCaptureRule");
                        maybeRemoveImport("org.springframework.boot.test.rule.OutputCapture");
                        maybeRemoveImport("org.junit.Rule");

                        return null;
                    }
                    return s;
                })));
                updateCursor(c);

                if (classDecl.getBody().getStatements().size() != c.getBody().getStatements().size()) {
                    JavaTemplate addOutputCaptureExtension = JavaTemplate.builder("@ExtendWith(OutputCaptureExtension.class)")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "spring-boot-test-2.+", "junit-jupiter-api-5.+"))
                            .imports("org.junit.jupiter.api.extension.ExtendWith",
                                    "org.springframework.boot.test.system.OutputCaptureExtension")
                            .build();

                    c = addOutputCaptureExtension.apply(
                        getCursor(),
                        c.getCoordinates()
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
        });
    }

    private static final MethodMatcher OUTPUT_CAPTURE_MATCHER = new MethodMatcher(
            "org.springframework.boot.test.rule.OutputCapture expect(..)"
    );
    private static final MethodMatcher OUTPUT_CAPTURE_RULE_MATCHER = new MethodMatcher(
            "org.springframework.boot.test.system.OutputCaptureRule expect(..)"
    );

    private static final class ConvertExpectMethods extends JavaIsoVisitor<ExecutionContext> {
        private final String variableName;

        private ConvertExpectMethods(String variableName) {
            this.variableName = variableName;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            if (!OUTPUT_CAPTURE_MATCHER.matches(m) && !OUTPUT_CAPTURE_RULE_MATCHER.matches(m)) {
                return m;
            }

            JavaTemplate matchesTemplate = JavaTemplate.builder("#{any()}.matches(#{}.getAll())")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-boot-test-2.+", "junit-jupiter-api-5.+"))
                    .build();
            m = matchesTemplate.apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0), variableName);
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
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            if (!FindMethods.find(m, "org.springframework.boot.test.rule.OutputCapture *(..)").isEmpty() ||
                    !FindMethods.find(m, "org.springframework.boot.test.system.OutputCaptureRule *(..)").isEmpty()) {
                // FIXME need addParameter coordinate here...
                // m = parameter.build().apply(m.getCoordinates().replaceParameters());

                J.VariableDeclarations param = new J.VariableDeclarations(Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        emptyList(),
                        emptyList(),
                        new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "CapturedOutput", CAPTURED_OUTPUT_TYPE, null),
                        null,
                        emptyList(),
                        singletonList(new JRightPadded<>(
                                new J.VariableDeclarations.NamedVariable(Tree.randomId(),
                                        Space.format(" "),
                                        Markers.EMPTY,
                                        new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), variableName, CAPTURED_OUTPUT_TYPE, null),
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

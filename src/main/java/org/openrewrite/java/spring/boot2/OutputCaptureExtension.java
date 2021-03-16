/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.java.ChangeMethodTargetToVariable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.text.RuleBasedCollator;
import java.util.Arrays;
import java.util.Comparator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class OutputCaptureExtension extends Recipe {
    @Override
    public String getDisplayName() {
        return "`@OutputCaptureRule` to `@ExtendWith(OutputCaptureExtension.class)`";
    }

    @Override
    public String getDescription() {
        return "Use the JUnit Jupiter extension instead of JUnit 4 rule.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate.Builder addOutputCaptureExtension = template("@ExtendWith(OutputCaptureExtension.class)")
                    .javaParser(JavaParser.fromJavaVersion()
                            .dependsOn(Arrays.asList(
                                    Parser.Input.fromString("package org.springframework.boot.test.system;\n" +
                                            "public class OutputCaptureExtension {}"),
                                    Parser.Input.fromString("package org.junit.jupiter.api.extension.ExtendWith;\n" +
                                            "public @interface ExtendWith {\n" +
                                            "  Class[] value();\n" +
                                            "}")
                            ))
                            .build())
                    .imports("org.junit.jupiter.api.extension.ExtendWith",
                            "org.springframework.boot.test.system.OutputCaptureExtension");

            @SneakyThrows
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext context) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, context);

                c = c.withBody(c.getBody().withStatements(ListUtils.map(c.getBody().getStatements(), s -> {
                    if (!(s instanceof J.VariableDeclarations)) {
                        return s;
                    }

                    J.VariableDeclarations field = (J.VariableDeclarations) s;
                    JavaType.Class fieldType = field.getTypeAsClass();
                    if (TypeUtils.isOfClassType(fieldType, "org.springframework.boot.test.system.OutputCaptureRule") ||
                            TypeUtils.isOfClassType(fieldType, "org.springframework.boot.test.rule.OutputCapture")) {
                        maybeRemoveImport("org.springframework.boot.test.system.OutputCaptureRule");
                        maybeRemoveImport("org.springframework.boot.test.rule.OutputCapture");

                        String fieldName = field.getVariables().get(0).getSimpleName();

                        doAfterVisit(new ChangeMethodTargetToVariable("org.springframework.boot.test.rule.OutputCapture *(..)",
                                fieldName, "org.springframework.boot.test.system.CapturedOutput"));
                        doAfterVisit(new ChangeMethodTargetToVariable("org.springframework.boot.test.system.OutputCaptureRule *(..)",
                                fieldName, "org.springframework.boot.test.system.CapturedOutput"));
                        doAfterVisit(new AddCapturedOutputParameter(fieldName));

                        getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, "addOutputCaptureExtension", true);

                        return null;
                    }
                    return s;
                })));

                if (classDecl.getBody().getStatements().size() != c.getBody().getStatements().size()) {
                    c = c.withTemplate(addOutputCaptureExtension.build(), c.getCoordinates()
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

    private static class AddCapturedOutputParameter extends JavaIsoVisitor<ExecutionContext> {
        private final String variableName;

        private AddCapturedOutputParameter(String variableName) {
            this.variableName = variableName;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext context) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, context);
            if (!FindMethods.find(m, "org.springframework.boot.test.system.CapturedOutput *(..)").isEmpty()) {
                // FIXME need addParameter coordinate here...
//                m = m.withTemplate(parameter.build(), m.getCoordinates().replaceParameters());

                JavaType.Class capturedOutputType = JavaType.Class.build("org.springframework.boot.test.system.CapturedOutput");
                J.VariableDeclarations param = new J.VariableDeclarations(Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        emptyList(),
                        emptyList(),
                        J.Identifier.build(Tree.randomId(), Space.EMPTY, Markers.EMPTY, "CapturedOutput", capturedOutputType),
                        null,
                        emptyList(),
                        singletonList(new JRightPadded<>(
                                new J.VariableDeclarations.NamedVariable(Tree.randomId(),
                                        Space.format(" "),
                                        Markers.EMPTY,
                                        J.Identifier.build(Tree.randomId(), Space.EMPTY, Markers.EMPTY, variableName, capturedOutputType),
                                        emptyList(),
                                        null,
                                        capturedOutputType),
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

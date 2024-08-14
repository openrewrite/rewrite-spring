/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.test;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

public class SpringRulesToJUnitExtension extends Recipe {
    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Replace JUnit 4 `SpringClassRule` and `SpringMethodRule` with JUnit 5 `SpringExtension`";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Replace JUnit 4 `SpringClassRule` and `SpringMethodRule` with JUnit 5 `SpringExtension` or rely on an existing `@SpringBootTest`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(new UsesType<>("org.springframework.test.context.junit4.rules.SpringClassRule", true), new UsesType<>("org.springframework.test.context.junit4.rules.SpringMethodRule", true)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                if (multiVariable.getTypeExpression() instanceof J.Identifier) {
                    J.Identifier id = (J.Identifier) multiVariable.getTypeExpression();
                    if (id.getSimpleName().equals("SpringClassRule") || id.getSimpleName().equals("SpringMethodRule")) {
                        maybeRemoveImport("org.springframework.test.context.junit4.rules.SpringClassRule");
                        maybeRemoveImport("org.springframework.test.context.junit4.rules.SpringMethodRule");
                        maybeRemoveImport("org.junit.ClassRule");
                        maybeRemoveImport("org.junit.Rule");
                        doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                            @Override
                            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                                if (classDecl.getLeadingAnnotations().stream().noneMatch(ann -> ann.getSimpleName().equals("SpringBootTest") || ann.getSimpleName().equals("ExtendWith"))) {
                                    maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                                    maybeAddImport("org.springframework.test.context.junit.jupiter.SpringExtension");
                                    return JavaTemplate.builder("@ExtendWith(SpringExtension.class)")
                                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "junit-jupiter-api", "spring-test"))
                                            .imports("org.junit.jupiter.api.extension.ExtendWith", "org.springframework.test.context.junit.jupiter.SpringExtension")
                                            .build().apply(getCursor(), classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                                }
                                return super.visitClassDeclaration(classDecl, executionContext);
                            }
                        });
                        return null;
                    }
                }
                return super.visitVariableDeclarations(multiVariable, executionContext);
            }
        });
    }
}

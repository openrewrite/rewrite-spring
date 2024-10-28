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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Comparator.comparing;

public class SpringRulesToJUnitExtension extends Recipe {

    private static final String SPRING_CLASS_RULE = "org.springframework.test.context.junit4.rules.SpringClassRule";
    private static final String SPRING_METHOD_RULE = "org.springframework.test.context.junit4.rules.SpringMethodRule";
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String SPRING_EXTENSION = "org.springframework.test.context.junit.jupiter.SpringExtension";
    private static final AnnotationMatcher ANNOTATION_MATCHER = new AnnotationMatcher(String.format("@%s(%s.class)", EXTEND_WITH, SPRING_EXTENSION), true);


    @Override
    public String getDisplayName() {
        return "Replace `SpringClassRule` and `SpringMethodRule` with JUnit 5 `SpringExtension`";
    }

    @Override
    public String getDescription() {
        return "Replace JUnit 4's `SpringClassRule` and `SpringMethodRule` with JUnit 5's `SpringExtension` or rely on an existing `@SpringBootTest`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(SPRING_CLASS_RULE, true),
                        new UsesType<>(SPRING_METHOD_RULE, true)
                ),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.@Nullable VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                        if (TypeUtils.isOfClassType(vd.getTypeAsFullyQualified(), SPRING_CLASS_RULE) ||
                            TypeUtils.isOfClassType(vd.getTypeAsFullyQualified(), SPRING_METHOD_RULE)) {
                            maybeRemoveImport(SPRING_CLASS_RULE);
                            maybeRemoveImport(SPRING_METHOD_RULE);
                            maybeRemoveImport("org.junit.ClassRule");
                            maybeRemoveImport("org.junit.Rule");

                            doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                                @Override
                                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                                    J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                                    if (cd.getLeadingAnnotations().stream().noneMatch(ANNOTATION_MATCHER::matches)) {
                                        maybeAddImport(EXTEND_WITH);
                                        maybeAddImport(SPRING_EXTENSION);
                                        return JavaTemplate.builder("@ExtendWith(SpringExtension.class)")
                                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api", "spring-test"))
                                                .imports(EXTEND_WITH, SPRING_EXTENSION)
                                                .build()
                                                .apply(getCursor(), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));

                                    }
                                    return cd;
                                }
                            });

                            //noinspection DataFlowIssue
                            return null;
                        }
                        return vd;
                    }
                });
    }
}

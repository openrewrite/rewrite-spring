/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.test;

import lombok.Getter;
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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Comparator.comparing;

public class ReplaceJUnit4SpringTestBaseClasses extends Recipe {

    private static final String ABSTRACT_JUNIT4 = "org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests";
    private static final String ABSTRACT_TRANSACTIONAL_JUNIT4 = "org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests";
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String SPRING_EXTENSION = "org.springframework.test.context.junit.jupiter.SpringExtension";
    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    private static final AnnotationMatcher EXTEND_WITH_SPRING_MATCHER =
            new AnnotationMatcher(String.format("@%s(%s.class)", EXTEND_WITH, SPRING_EXTENSION), true);
    private static final AnnotationMatcher TRANSACTIONAL_MATCHER =
            new AnnotationMatcher("@" + TRANSACTIONAL, true);

    @Getter
    final String displayName = "Replace JUnit 4 Spring test base classes with JUnit Jupiter annotations";

    @Getter
    final String description = "Replace `AbstractJUnit4SpringContextTests` and `AbstractTransactionalJUnit4SpringContextTests` " +
            "base classes with `@ExtendWith(SpringExtension.class)` and `@Transactional` annotations. " +
            "These base classes are deprecated in Spring Framework 7.0 in favor of the SpringExtension for JUnit Jupiter.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(ABSTRACT_JUNIT4, true),
                        new UsesType<>(ABSTRACT_TRANSACTIONAL_JUNIT4, true)
                ),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (cd.getExtends() == null) {
                            return cd;
                        }

                        boolean isTransactional = TypeUtils.isOfClassType(cd.getExtends().getType(), ABSTRACT_TRANSACTIONAL_JUNIT4);
                        boolean isAbstractJUnit4 = isTransactional || TypeUtils.isOfClassType(cd.getExtends().getType(), ABSTRACT_JUNIT4);

                        if (!isAbstractJUnit4) {
                            return cd;
                        }

                        // Remove extends clause
                        cd = cd.withExtends(null);
                        JavaType.Class type = (JavaType.Class) cd.getType();
                        if (type != null) {
                            cd = cd.withType(type.withSupertype(null));
                        }
                        updateCursor(cd);

                        maybeRemoveImport(ABSTRACT_JUNIT4);
                        maybeRemoveImport(ABSTRACT_TRANSACTIONAL_JUNIT4);

                        // Add @ExtendWith(SpringExtension.class) if not already present
                        if (cd.getLeadingAnnotations().stream().noneMatch(EXTEND_WITH_SPRING_MATCHER::matches)) {
                            maybeAddImport(EXTEND_WITH);
                            maybeAddImport(SPRING_EXTENSION);
                            cd = JavaTemplate.builder("@ExtendWith(SpringExtension.class)")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api", "spring-test"))
                                    .imports(EXTEND_WITH, SPRING_EXTENSION)
                                    .build()
                                    .apply(getCursor(), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                            updateCursor(cd);
                        }

                        // Add @Transactional if the class extended AbstractTransactionalJUnit4SpringContextTests
                        if (isTransactional && cd.getLeadingAnnotations().stream().noneMatch(TRANSACTIONAL_MATCHER::matches)) {
                            maybeAddImport(TRANSACTIONAL);
                            cd = JavaTemplate.builder("@Transactional")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-tx"))
                                    .imports(TRANSACTIONAL)
                                    .build()
                                    .apply(getCursor(), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                            updateCursor(cd);
                        }

                        return cd;
                    }

                    @Override
                    public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        // Remove constructors that only call super()
                        if (md.isConstructor() && overridesBaseClass(md)) {
                            if (md.getBody() != null && md.getBody().getStatements().isEmpty()) {
                                //noinspection DataFlowIssue
                                return null;
                            }
                        }
                        return md;
                    }

                    private boolean overridesBaseClass(J.MethodDeclaration md) {
                        return java.util.Optional.ofNullable(md.getMethodType())
                                .map(JavaType.Method::getDeclaringType)
                                .map(JavaType.FullyQualified::getSupertype)
                                .filter(type -> type.isAssignableTo(ABSTRACT_JUNIT4) || type.isAssignableTo(ABSTRACT_TRANSACTIONAL_JUNIT4))
                                .isPresent();
                    }
                });
    }
}

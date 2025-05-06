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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class UnnecessarySpringExtension extends Recipe {

    // All the following annotations apply the @SpringExtension
    private static final List<String> SPRING_BOOT_TEST_ANNOTATIONS = Arrays.asList(
            "org.springframework.boot.test.context.SpringBootTest",
            "org.springframework.boot.test.autoconfigure.jdbc.JdbcTest",
            "org.springframework.boot.test.autoconfigure.web.client.RestClientTest",
            "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest",
            "org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest",
            "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest",
            "org.springframework.boot.test.autoconfigure.webservices.client.WebServiceClientTest",
            "org.springframework.boot.test.autoconfigure.jooq.JooqTest",
            "org.springframework.boot.test.autoconfigure.json.JsonTest",
            "org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest",
            "org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest",
            "org.springframework.boot.test.autoconfigure.data.ldap.DataLdapTest",
            "org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest",
            "org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest",
            "org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest",
            "org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest",
            "org.springframework.batch.test.context.SpringBatchTest",
            "org.springframework.test.context.junit.jupiter.SpringJUnitConfig"
    );

    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String EXTEND_WITH_ANNOTATION = "@" + EXTEND_WITH;
    private static final AnnotationMatcher EXTEND_WITH_MATCHER = new AnnotationMatcher(EXTEND_WITH_ANNOTATION);
    private static final String SPRING_EXTENSION = "org.springframework.test.context.junit.jupiter.SpringExtension";
    private static final String EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN = EXTEND_WITH_ANNOTATION + "(" + SPRING_EXTENSION + ".class)";

    @Override
    public String getDisplayName() {
        return "Remove `@SpringExtension`";
    }

    @Override
    public String getDescription() {
        return "`@SpringBootTest` and all test slice annotations already applies `@SpringExtension` as of Spring Boot 2.1.0.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(SPRING_EXTENSION, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

                        // Clear the class body to make annotation search and replace faster
                        // noinspection ConstantConditions
                        J.ClassDeclaration c = classDecl.withBody(null);

                        AtomicBoolean annotationFound = new AtomicBoolean(false);
                        new FindBootTestAnnotation().visit(c, annotationFound);

                        if (annotationFound.get()) {
                            Set<J.Annotation> extendsWiths = FindAnnotations.find(c, EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN);
                            if (!extendsWiths.isEmpty()) {
                                Expression expression = extendsWiths.iterator().next().getArguments().get(0);
                                if ((expression instanceof J.FieldAccess) || (expression instanceof J.NewArray &&
                                        ((J.NewArray) expression).getInitializer() != null &&
                                        (((J.NewArray) expression).getInitializer().size() == 1))) {
                                    c = (J.ClassDeclaration) new RemoveAnnotation(EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN)
                                            .getVisitor().visit(c, ctx, getCursor().getParentOrThrow());
                                    assert c != null;
                                    maybeRemoveImport(SPRING_EXTENSION);
                                    maybeRemoveImport(EXTEND_WITH);
                                    return super.visitClassDeclaration(c.withBody(classDecl.getBody()), ctx);
                                }
                            }
                            return super.visitClassDeclaration(classDecl, ctx);
                        }
                        return classDecl;
                        //return super.visitClassDeclaration(classDecl, ctx);
                    }

                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        if (EXTEND_WITH_MATCHER.matches(annotation)) {

                            Expression expression = annotation.getArguments().get(0);

                            if (expression instanceof J.NewArray) {
                                List<Expression> collected = ((J.NewArray) expression).getInitializer().stream()
                                        .filter(e -> !isSpringExtension(e))
                                        .collect(Collectors.toList());
                                Expression newArgument = ((J.NewArray) expression).withInitializer(collected);
                                J.Annotation newAnnotation = annotation.withArguments(Collections.singletonList(newArgument));
                                maybeRemoveImport(SPRING_EXTENSION);
                                return maybeAutoFormat(annotation, newAnnotation, ctx);
                            }

                            return annotation;
                        }

                        return super.visitAnnotation(annotation, ctx);
                    }

                    private boolean isSpringExtension(Expression expression) {
                        return TypeUtils.isAssignableTo("java.lang.Class<" + SPRING_EXTENSION + ">", expression.getType());
                    }
                });
    }

    // Using this visitor vs making 15 calls to findAnnotations.
    private static class FindBootTestAnnotation extends JavaIsoVisitor<AtomicBoolean> {

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, AtomicBoolean found) {
            J.Annotation a = super.visitAnnotation(annotation, found);
            if (!found.get()) {
                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(a.getType());
                if (fullyQualified != null && SPRING_BOOT_TEST_ANNOTATIONS.contains(fullyQualified.getFullyQualifiedName())) {
                    found.set(true);
                }
            }
            return a;
        }
    }
}

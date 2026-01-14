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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;

public class UnnecessarySpringExtension extends Recipe {

    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String SPRING_EXTENSION = "org.springframework.test.context.junit.jupiter.SpringExtension";
    private static final String EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN = String.format("@%s(%s.class)", EXTEND_WITH, SPRING_EXTENSION);
    private static final AnnotationMatcher EXTENDS_WITH_SPRING_EXACT_MATCHER = new AnnotationMatcher(EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN, false);

    @Getter
    final String displayName = "Remove `@SpringExtension`";

    @Getter
    final String description = "`@SpringBootTest` and all test slice annotations already applies `@SpringExtension` as of Spring Boot 2.1.0.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(SPRING_EXTENSION, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        Set<J.Annotation> extendsWithMetaAnnotations = FindAnnotations.find(classDecl, EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN, true);
                        if (1 < extendsWithMetaAnnotations.size() || extendsWithMetaAnnotations.size() == 1 && usesOlderSpringTestAnnotation(classDecl)) {
                            return classDecl.withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(), annotation -> {
                                if (EXTENDS_WITH_SPRING_EXACT_MATCHER.matches(annotation)) {
                                    Expression expression = annotation.getArguments().get(0);
                                    if (expression instanceof J.FieldAccess) {
                                        doAfterVisit(new RemoveAnnotationVisitor(EXTENDS_WITH_SPRING_EXACT_MATCHER));
                                        maybeRemoveImport(SPRING_EXTENSION);
                                    } else if (expression instanceof J.NewArray &&
                                            ((J.NewArray) expression).getInitializer() != null) {
                                        if (((J.NewArray) expression).getInitializer().size() == 1) {
                                            doAfterVisit(new RemoveAnnotationVisitor(EXTENDS_WITH_SPRING_EXACT_MATCHER));
                                            maybeRemoveImport(SPRING_EXTENSION);
                                        } else {
                                            maybeRemoveImport(EXTEND_WITH);
                                            maybeRemoveImport(SPRING_EXTENSION);
                                            J.Annotation newAnnotation = annotation.withArguments(singletonList(((J.NewArray) expression)
                                                    .withInitializer(ListUtils.map(((J.NewArray) expression).getInitializer(),
                                                            e -> TypeUtils.isAssignableTo("java.lang.Class<" + SPRING_EXTENSION + ">", e.getType()) ? null : e))));
                                            return maybeAutoFormat(annotation, newAnnotation, ctx);
                                        }
                                    }
                                }
                                return annotation;
                            }));
                        }
                        return super.visitClassDeclaration(classDecl, ctx);
                    }

                    private final List<String> SPRING_BOOT_TEST_ANNOTATIONS = Arrays.asList(
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

                    /**
                     * When upgrading from 1.5 the `@SpringBootTest` annotation is not yet meta annotated.
                     */
                    private boolean usesOlderSpringTestAnnotation(J.ClassDeclaration classDecl) {
                        return classDecl.getLeadingAnnotations().stream()
                                .anyMatch(leadingAnnotation -> {
                                    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(leadingAnnotation.getType());
                                    return fullyQualified != null && SPRING_BOOT_TEST_ANNOTATIONS.contains(fullyQualified.getFullyQualifiedName());
                                });
                    }
                });
    }
}

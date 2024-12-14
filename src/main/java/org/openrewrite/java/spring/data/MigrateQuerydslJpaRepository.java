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
package org.openrewrite.java.spring.data;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;


public class MigrateQuerydslJpaRepository extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `QuerydslPredicateExecutor<T>`";
    }

    @Override
    public String getDescription() {
        return "`QuerydslJpaRepository<T, ID extends Serializable>` was deprecated in Spring Data 2.1.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.data.jpa.repository.support.QuerydslJpaRepository", false), new JavaVisitor<ExecutionContext>() {
            final String originalFqn = "org.springframework.data.jpa.repository.support.QuerydslJpaRepository";
            final String targetFqn = "org.springframework.data.jpa.repository.support.QuerydslJpaPredicateExecutor";

            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = (J.CompilationUnit) super.visitCompilationUnit(cu, ctx);
                doAfterVisit(new ChangeType(originalFqn, targetFqn, false).getVisitor());
                return c;
            }

            @Override
            public J visitParameterizedType(J.ParameterizedType type, ExecutionContext ctx) {
                J.ParameterizedType t = (J.ParameterizedType) super.visitParameterizedType(type, ctx);
                if (t.getClazz() instanceof J.Identifier && TypeUtils.isOfType(TypeUtils.asFullyQualified(t.getClazz().getType()),
                        TypeUtils.asFullyQualified(JavaType.ShallowClass.build(originalFqn))) &&
                        t.getTypeParameters() != null && t.getTypeParameters().size() == 2) {
                    t = t.withTypeParameters(t.getTypeParameters().subList(0, 1));
                }
                return t;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                if (newClass.getClazz() != null && TypeUtils.isOfClassType(newClass.getClazz().getType(), originalFqn)) {
                    String template = "new QuerydslJpaPredicateExecutor(#{any(org.springframework.data.jpa.repository.support.JpaEntityInformation)}, " +
                            "#{any(javax.persistence.EntityManager)}, " +
                            "#{any(org.springframework.data.querydsl.EntityPathResolver)}, null)";

                    J.FieldAccess entityPathResolver = TypeTree.build("SimpleEntityPathResolver.INSTANCE");
                    return JavaTemplate.builder(template)
                        .imports(targetFqn)
                        .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx,
                                "javax.persistence-api-2.*",
                                "spring-data-commons-2.*",
                                "spring-data-jpa-2.*"
                            ))
                        .build().apply(
                            getCursor(),
                            newClass.getCoordinates().replace(),
                            newClass.getArguments().get(0),
                            newClass.getArguments().get(1),
                            newClass.getArguments().size() == 3 ? newClass.getArguments().get(2) : entityPathResolver);
                }
                return super.visitNewClass(newClass, ctx);
            }
        });
    }
}

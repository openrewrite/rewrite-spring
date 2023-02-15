/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.search;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.spring.table.SpringComponentRelationships;
import org.openrewrite.java.spring.table.SpringComponents;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import static java.util.Objects.requireNonNull;

public class FindSpringComponents extends Recipe {
    transient SpringComponents springComponents = new SpringComponents(this);
    transient SpringComponentRelationships componentRelationships = new SpringComponentRelationships(this);

    @Override
    public String getDisplayName() {
        return "Find Spring components";
    }

    @Override
    public String getDescription() {
        return "Find Spring components, including controllers, services, repositories, " +
               "return types of `@Bean` annotated methods, etc.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                    if (TypeUtils.isAssignableTo("org.springframework.stereotype.Component", annotation.getType())) {
                        c = SearchResult.found(c, "component");
                        springComponents.insertRow(ctx, new SpringComponents.Row(
                                getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                                requireNonNull(classDecl.getType()).getFullyQualifiedName()
                        ));
                        recordConstructorInjections(c, ctx);
                        break;
                    }
                }
                return c;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (!FindAnnotations.find(m, "@org.springframework.context.annotation.Bean").isEmpty() &&
                    m.getReturnTypeExpression() != null) {

                    m = SearchResult.found(m, "bean");
                    recordDependencies(TypeUtils.asFullyQualified(requireNonNull(m.getReturnTypeExpression()).getType()), m, ctx);
                }
                return m;
            }

            private void recordConstructorInjections(J.ClassDeclaration c, ExecutionContext ctx) {
                int ctorCount = 0;
                J.MethodDeclaration ctor = null;
                for (Statement statement : c.getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration) {
                        J.MethodDeclaration m = (J.MethodDeclaration) statement;
                        if (m.isConstructor()) {
                            boolean autowired = !FindAnnotations.find(m, "@org.springframework.beans.factory.annotation.Autowired").isEmpty();
                            if (autowired || m.hasModifier(J.Modifier.Type.Public)) {
                                ctor = m;
                                ctorCount++;
                            }
                            if (autowired) {
                                ctorCount = 1;
                                break;
                            }
                        }
                    }
                }

                // single constructors are implicitly autowired
                if (ctor != null && ctorCount == 1) {
                    recordDependencies(c.getType(), ctor, ctx);
                }
            }

            private void recordDependencies(@Nullable JavaType.FullyQualified dependentType, J.MethodDeclaration m, ExecutionContext ctx) {
                if (dependentType == null) {
                    return;
                }
                for (Statement dependency : m.getParameters()) {
                    if (dependency instanceof J.VariableDeclarations) {
                        JavaType.FullyQualified depType = ((J.VariableDeclarations) dependency).getTypeAsFullyQualified();
                        if (depType != null) {
                            componentRelationships.insertRow(ctx, new SpringComponentRelationships.Row(
                                    getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                                    dependentType.getFullyQualifiedName(),
                                    depType.getFullyQualifiedName()
                            ));
                        }
                    }
                }
            }
        };
    }
}

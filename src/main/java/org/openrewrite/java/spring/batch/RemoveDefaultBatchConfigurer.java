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
package org.openrewrite.java.spring.batch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markup;

import java.util.List;
import java.util.Optional;

public class RemoveDefaultBatchConfigurer extends Recipe {

    private static final String BATCH_CONFIGURER = "org.springframework.batch.core.configuration.annotation.BatchConfigurer";
    private static final String DEFAULT_BATCH_CONFIGURER = "org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer";

    @Override
    public String getDisplayName() {
        return "Remove `DefaultBatchConfigurer`";
    }

    @Override
    public String getDescription() {
        return "Remove `extends DefaultBatchConfigurer` and `@Override` from associated methods.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(DEFAULT_BATCH_CONFIGURER, true), new RemoveDefaultBatchConfigurerVisitor());
    }

    static final class RemoveDefaultBatchConfigurerVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            if (TypeUtils.isAssignableTo(DEFAULT_BATCH_CONFIGURER, cd.getType())) {
                // Strip extends DefaultBatchConfigurer
                maybeRemoveImport(DEFAULT_BATCH_CONFIGURER);
                return cd.withExtends(null);
            }
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            if (overridesDefaultBatchConfigurerMethod(md) || callsDefaultBatchConfigurerSuperConstructor(md)) {
                // Strip @Override
                md = md.withLeadingAnnotations(ListUtils.map(md.getLeadingAnnotations(),
                        a -> TypeUtils.isAssignableTo("java.lang.Override", a.getType()) ? null : a));
                md = Markup.info(md, "TODO Used to override a DefaultBatchConfigurer method; reconsider if still needed");

                // Strip calls to super()
                md = md.withBody(md.getBody().withStatements(ListUtils.map(md.getBody().getStatements(),
                        s -> s instanceof J.MethodInvocation && "super".equals(((J.MethodInvocation) s).getSimpleName()) ? null : s)));

                // Strip calls to super.*()
                md = md.withBody(md.getBody().withStatements(ListUtils.map(md.getBody().getStatements(),
                        s -> s instanceof J.MethodInvocation && ((J.MethodInvocation) s).getSelect() instanceof J.Identifier &&
                              "super".equals(((J.Identifier) ((J.MethodInvocation) s).getSelect()).getSimpleName()) ? null : s)));

                // Strip (now) empty methods
                if (md.getBody().getStatements().isEmpty()) {
                    return null;
                }
            }

            // Strip calls to new DefaultBatchConfigurer()
            List<Statement> statements = md.getBody().getStatements();
            if (statements.size() == 1
                && statements.get(0) instanceof J.Return
                && new MethodMatcher(DEFAULT_BATCH_CONFIGURER + " <constructor>(..)")
                        .matches(((J.Return) statements.get(0)).getExpression())) {
                maybeRemoveImport(BATCH_CONFIGURER);
                maybeRemoveImport(DEFAULT_BATCH_CONFIGURER);
                return null;
            }

            return md;
        }

        private static boolean overridesDefaultBatchConfigurerMethod(J.MethodDeclaration md) {
            return Optional.ofNullable(md.getMethodType())
                    .map(JavaType.Method::getDeclaringType)
                    .map(JavaType.FullyQualified::getSupertype)
                    .filter(type -> type.isAssignableTo(DEFAULT_BATCH_CONFIGURER))
                    .flatMap(type -> TypeUtils.findDeclaredMethod(type, md.getSimpleName(), md.getMethodType().getParameterTypes()))
                    .isPresent();
        }

        private static boolean callsDefaultBatchConfigurerSuperConstructor(J.MethodDeclaration md) {
            return md.isConstructor() && Optional.ofNullable(md.getMethodType())
                    .map(JavaType.Method::getDeclaringType)
                    .filter(type -> type.isAssignableTo(DEFAULT_BATCH_CONFIGURER))
                    .isPresent();
        }
    }
}

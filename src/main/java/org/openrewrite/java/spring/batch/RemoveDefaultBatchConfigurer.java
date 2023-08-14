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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markup;

import java.util.List;

public class RemoveDefaultBatchConfigurer extends Recipe {

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
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
            if (TypeUtils.isAssignableTo(DEFAULT_BATCH_CONFIGURER, cd.getType())) {
                // Strip extends DefaultBatchConfigurer
                maybeRemoveImport(DEFAULT_BATCH_CONFIGURER);
                return cd.withExtends(null);
            }
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
            if (TypeUtils.isAssignableTo(DEFAULT_BATCH_CONFIGURER, method.getMethodType().getDeclaringType())) {
                // Strip @Override
                List<J.Annotation> formerLeadingAnnotations = md.getLeadingAnnotations();
                if (TypeUtils.isOverride(md.getMethodType())) {
                    md = md.withLeadingAnnotations(ListUtils.map(formerLeadingAnnotations,
                            a -> (TypeUtils.isAssignableTo("java.lang.Override", a.getType())) ? null : a));
                    md = Markup.info(md, "TODO Used to override a DefaultBatchConfigurer method; reconsider if still needed");
                }

                // Strip calls to super
                md = md.withBody(md.getBody().withStatements(ListUtils.map(md.getBody().getStatements(),
                        s -> (s instanceof J.MethodInvocation && ((J.MethodInvocation) s).getSelect() instanceof J.Identifier &&
                                ((J.Identifier) ((J.MethodInvocation) s).getSelect()).getSimpleName().equals("super")) ? null : s)));

                // Strip (now) empty methods
                if (md.getBody().getStatements().isEmpty()) {
                    return null;
                }
            }
            return md;
        }
    }
}

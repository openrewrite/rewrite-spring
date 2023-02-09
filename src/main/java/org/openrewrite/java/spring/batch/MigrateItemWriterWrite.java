/*
 * Copyright 2021 the original author or authors.
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

import java.time.Duration;

import org.openrewrite.Applicability;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.search.FindImports;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Collections.singletonList;

public class MigrateItemWriterWrite extends Recipe {


    @Override
    public String getDisplayName() {
        return "Migrate `ItemWriter`";
    }

    @Override
    public String getDescription() {
        return "`JobBuilderFactory` was deprecated in Springbatch 5.x : replaced by `JobBuilder`.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(new MethodMatcher(
                 "*..* write(java.util.List)" 
                ));
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private String fullyQualifiedInterfaceName = "org.springframework.batch.item.ItemWriter";

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                if (m.getMethodType() != null && (TypeUtils.isOverride(m.getMethodType())
                        || m.getMethodType().isInheritedFrom(fullyQualifiedInterfaceName))) {
                    if (m.getParameters().size() == 1) {
                        Statement parameter = m.getParameters().get(0);
                        if (parameter instanceof J.VariableDeclarations) {
                            J.VariableDeclarations param = (J.VariableDeclarations) parameter;
                            if (TypeUtils.isOfClassType(param.getType(), "java.util.List")) {

                                param = (J.VariableDeclarations) new ChangeType("java.util.List",
                                        "org.springframework.batch.item.Chunk", false).getVisitor()
                                        .visitNonNull(param, ctx, getCursor().getParentOrThrow());

                                m = m.withParameters(singletonList(param));

                                maybeAddImport("org.springframework.batch.item.Chunk");
                                maybeRemoveImport("java.util.List");
                            }
                        }
                    }
                }
                return m;
            }
        };
    }
}

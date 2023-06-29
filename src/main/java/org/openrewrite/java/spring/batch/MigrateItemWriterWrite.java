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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MigrateItemWriterWrite extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate `ItemWriter`";
    }

    @Override
    public String getDescription() {
        return "`JobBuilderFactory` was deprecated in Springbatch 5.x : replaced by `JobBuilder`.";
    }

    private static final MethodMatcher ITEM_WRITER_MATCHER = new MethodMatcher("org.springframework.batch.item.ItemWriter write(java.util.List)", true);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresMethod<>(ITEM_WRITER_MATCHER), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                if (!ITEM_WRITER_MATCHER.matches(method.getMethodType())) {
                    return m;
                }

                J.VariableDeclarations parameter = (J.VariableDeclarations) m.getParameters().get(0);
                if (!(parameter.getTypeExpression() instanceof J.ParameterizedType)
                        || ((J.ParameterizedType) parameter.getTypeExpression()).getTypeParameters() == null) {
                    return m;
                }
                String chunkTypeParameter = ((J.ParameterizedType) parameter.getTypeExpression()).getTypeParameters().get(0).toString();
                String paramName = parameter.getVariables().get(0).getSimpleName();

                // @Override may or may not already be present
                String annotationsWithOverride = Stream.concat(
                                m.getAllAnnotations().stream().map(it -> it.print(getCursor())),
                                Stream.of("@Override"))
                        .distinct()
                        .collect(Collectors.joining("\n"));

                // Should be able to replace just the parameters and have usages of those parameters get their types
                // updated automatically. Since parameters usages do not have their type updated, must replace the whole
                // method to ensure that type info is accurate / List import can potentially be removed
                // See: https://github.com/openrewrite/rewrite/issues/2819

                m = JavaTemplate.builder("#{}\n #{} void write(#{} Chunk<#{}> #{}) throws Exception #{}")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "spring-batch-core-5.+", "spring-batch-infrastructure-5.+"))
                    .imports("org.springframework.batch.item.Chunk")
                    .build()
                    .apply(
                        getCursor(),
                        m.getCoordinates().replace(),
                        annotationsWithOverride,
                        m.getModifiers().stream()
                            .map(J.Modifier::toString)
                            .collect(Collectors.joining(" ")),
                        parameter.getModifiers().stream()
                            .map(J.Modifier::toString)
                            .collect(Collectors.joining(" ")),
                        chunkTypeParameter,
                        paramName,
                        m.getBody() == null ? "" : m.getBody().print(getCursor()));

                maybeAddImport("org.springframework.batch.item.Chunk");
                maybeRemoveImport("java.util.List");

                return m;
            }
        });
    }
}

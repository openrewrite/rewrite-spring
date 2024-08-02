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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MigrateItemWriterWrite extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate `ItemWriter`";
    }

    @Override
    public String getDescription() {
        return "`JobBuilderFactory` was deprecated in spring-batch 5.x: replaced by `JobBuilder`.";
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
                                service(AnnotationService.class)
                                        .getAllAnnotations(getCursor()).stream()
                                        .map(it -> it.print(getCursor())),
                                Stream.of("@Override"))
                        .distinct()
                        .collect(Collectors.joining("\n"));

                m = new UpdateListMethodInvocations(paramName).visitMethodDeclaration(m, ctx);
                updateCursor(m);

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

    private static class UpdateListMethodInvocations extends JavaIsoVisitor<ExecutionContext> {
        private static final String ITERABLE_FQN = "java.lang.Iterable";
        private static final String GET_ITEMS_METHOD = "getItems";
        private final String parameterName;

        public UpdateListMethodInvocations(String parameterName) {
            this.parameterName = parameterName;
        }

        private static final MethodMatcher LIST_MATCHER = new MethodMatcher("java.util.List *(..)", true);

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (LIST_MATCHER.matches(mi) && isParameter(mi.getSelect())) {
                assert mi.getPadding().getSelect() != null;
                // No need to take care of typing here, since it's going to be printed and parsed on the JavaTemplate later on.
                mi = mi.withSelect(newGetItemsMethodInvocation(mi.getPadding().getSelect()));

            }
            if (!ITEM_WRITER_MATCHER.matches(mi) && mi.getMethodType() != null) {
                List<JavaType> parameterTypes = mi.getMethodType().getParameterTypes();
                mi = mi.withArguments(ListUtils.map(mi.getArguments(), (i, e) -> {
                    if (isParameter(e)) {
                        JavaType type = parameterTypes.size() > i ?
                                parameterTypes.get(i) :
                                parameterTypes.get(parameterTypes.size() - 1);

                        if (notAssignableFromChunk(type)) {
                            return newGetItemsMethodInvocation(
                                    new JRightPadded<>(e, Space.EMPTY, Markers.EMPTY)
                            );
                        }
                    }
                    return e;
                }));
            }
            return mi;
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
            J.VariableDeclarations.NamedVariable var = super.visitVariable(variable, ctx);

            if (notAssignableFromChunk(var) && isParameter(var.getInitializer())) {
                var = var.withInitializer(newGetItemsMethodInvocation(
                        new JRightPadded<>(var.getInitializer(), Space.EMPTY, Markers.EMPTY)
                ));
            }
            return var;
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
            J.Assignment a = super.visitAssignment(assignment, ctx);
            if (notAssignableFromChunk(a.getVariable().getType()) && isParameter(a.getAssignment())) {
                a = a.withAssignment(newGetItemsMethodInvocation(
                        new JRightPadded<>(a.getAssignment(), Space.EMPTY, Markers.EMPTY)
                ));
            }
            return a;
        }

        private boolean notAssignableFromChunk(J.VariableDeclarations.NamedVariable var) {
            return var.getVariableType() != null && notAssignableFromChunk(var.getVariableType().getType());
        }

        private boolean notAssignableFromChunk(@Nullable JavaType type) {
            // Iterable is the only common type between List and Chunk
            return !TypeUtils.isOfClassType(type, ITERABLE_FQN);
        }

        private boolean isParameter(@Nullable Expression maybeParameter) {
            return maybeParameter instanceof J.Identifier &&
                   ((J.Identifier) maybeParameter).getFieldType() != null &&
                   ((J.Identifier) maybeParameter).getFieldType().getName().equals(parameterName);
        }

        private static J.MethodInvocation newGetItemsMethodInvocation(JRightPadded<Expression> select) {
            return new J.MethodInvocation(
                    Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                    select, null,
                    newGetItemsIdentifier(),
                    JContainer.empty(),
                    null
            );
        }

        private static J.Identifier newGetItemsIdentifier() {
            return new J.Identifier(
                    Tree.randomId(),
                    Space.EMPTY, Markers.EMPTY, Collections.emptyList(),
                    GET_ITEMS_METHOD,
                    null, null);
        }
    }
}

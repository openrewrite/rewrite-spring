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
package org.openrewrite.java.spring.batch;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;

import java.util.regex.Pattern;

public class MigrateJobParameter extends Recipe {

    private static final String JOBPARAMETER = "org.springframework.batch.core.JobParameter";

    @Override
    public String getDisplayName() {
        return "Add class argument to `JobParameters`";
    }

    @Override
    public String getDescription() {
        return "Migration Job Parameter, parameterized type is essential in Spring Batch 5.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(JOBPARAMETER, true),
                        new UsesType<>("org.springframework.batch.core.JobParameters", true)
                ),
                new JavaIsoVisitor<ExecutionContext>() {

                    private boolean defineMapTypeWithJobParameter(@Nullable JavaType type) {
                        if (type != null && type.isAssignableFrom(Pattern.compile("java.util.Map")) &&
                                type instanceof JavaType.Parameterized) {
                            return ((JavaType.Parameterized) type).getTypeParameters().get(1).isAssignableFrom(Pattern.compile(JOBPARAMETER)) &&
                                    !(((JavaType.Parameterized) type).getTypeParameters().get(1) instanceof JavaType.Parameterized);
                        }
                        return false;
                    }

                    private boolean defineMapEntryTypeWithJobParameter(@Nullable JavaType type) {
                        if (type != null && type.isAssignableFrom(Pattern.compile("java.util.Map.Entry")) &&
                                type instanceof JavaType.Parameterized) {
                            return ((JavaType.Parameterized) type).getTypeParameters().get(1).isAssignableFrom(Pattern.compile(JOBPARAMETER)) &&
                                    !(((JavaType.Parameterized) type).getTypeParameters().get(1) instanceof JavaType.Parameterized);
                        }
                        return false;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        method = super.visitMethodInvocation(method, ctx);
                        if (method.getType() != null &&
                                method.getType().isAssignableFrom(Pattern.compile("java.util.Map")) &&
                                method.getName().getSimpleName().equals("of") && method.getArguments().size() % 2 == 0) {
                            return method.withTypeParameters(null);
                        }
                        return method;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        multiVariable = super.visitVariableDeclarations(multiVariable, ctx);
                        if (defineMapTypeWithJobParameter(multiVariable.getType())) {
                            multiVariable = new JNewClassOfMap().visitVariableDeclarations(multiVariable, ctx);
                            maybeAddImport("java.util.Map");
                            return multiVariable.withTypeExpression(TypeTree.build("Map<String, JobParameter<?>>")
                                            .withPrefix(multiVariable.getTypeExpression().getPrefix()))
                                    .withType(JavaType.buildType("java.util.Map"));
                        } else if (defineMapEntryTypeWithJobParameter(multiVariable.getType())) {
                            maybeAddImport("java.util.Map");
                            return multiVariable.withTypeExpression(TypeTree.build("Map.Entry<String, JobParameter<?>>")
                                            .withPrefix(multiVariable.getTypeExpression().getPrefix()))
                                    .withType(JavaType.buildType("java.util.Map.Entry"));
                        }
                        return multiVariable;
                    }


                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                        J.Assignment ass = super.visitAssignment(assignment, ctx);
                        return new JNewClassOfMap().visitAssignment(ass, ctx);
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        method = super.visitMethodDeclaration(method, ctx);

                        if (method.getReturnTypeExpression() != null && defineMapTypeWithJobParameter(method.getReturnTypeExpression().getType())) {
                            method = method.withReturnTypeExpression(TypeTree.build("Map<String, JobParameter<?>>")
                                    .withPrefix(method.getReturnTypeExpression().getPrefix()));
                            doAfterVisit(new AddImport<>("java.util.Map", null, false));
                        } else if (method.getReturnTypeExpression() != null && defineMapEntryTypeWithJobParameter(method.getReturnTypeExpression().getType())) {
                            method = method.withReturnTypeExpression(TypeTree.build("Map.Entry<String, JobParameter<?>>")
                                    .withPrefix(method.getReturnTypeExpression().getPrefix()));
                            doAfterVisit(new AddImport<>("java.util.Map", null, false));
                        }
                        return method;
                    }

                    @Contract("null -> null")
                    private @Nullable String typeString(@Nullable JavaType javaType) {
                        if (javaType instanceof JavaType.Primitive) {
                            return ((JavaType.Primitive) javaType).name();
                        } else if (javaType instanceof JavaType.Class) {
                            return ((JavaType.Class) javaType).getClassName();
                        } else if (javaType instanceof JavaType.Array) {
                            return javaType.toString();
                        }
                        return null;
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = super.visitNewClass(newClass, ctx);
                        if (nc.getClazz() != null &&
                                nc.getClazz().getType() != null &&
                                nc.getClazz().getType().isAssignableFrom(Pattern.compile("org.springframework.batch.core.JobParameter"))) {
                            if(newClass.getArguments().stream().filter(expression -> expression.getType()!=null).anyMatch(expression -> expression.getType().isAssignableFrom(Pattern.compile("java.lang.Class")))) {
                                return newClass;
                            }
                            JavaType javaType = nc.getArguments().get(0).getType();
                            String typeString = typeString(javaType);
                            if (typeString == null) {
                                return nc;
                            }

                            if (nc.getArguments().size() > 1) {
                                return JavaTemplate.builder("new JobParameter<>(#{any()}, #{}.class, #{any()})")
                                        .imports("org.springframework.batch.core.JobParameter")
                                        .javaParser(JavaParser.fromJavaVersion()
                                                .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-batch-infrastructure-5.1.+"))
                                        .build()
                                        .apply(getCursor(), nc.getCoordinates().replace(),
                                                nc.getArguments().get(0),
                                                typeString,
                                                nc.getArguments().get(1));
                            }
                            return JavaTemplate.builder("new JobParameter<>(#{any()}, #{}.class)")
                                    .imports("org.springframework.batch.core.JobParameter")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-batch-infrastructure-5.1.+"))
                                    .build()
                                    .apply(getCursor(), nc.getCoordinates().replace(),
                                            nc.getArguments().get(0),
                                            typeString);
                        }
                        return nc;
                    }
                }
        );
    }

    private static class JNewClassOfMap extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            J.NewClass nc = super.visitNewClass(newClass, ctx);
            if (nc.getType() != null &&
                    nc.getType().isAssignableFrom(Pattern.compile("java.util.Map")) &&
                    nc.getClazz() instanceof J.ParameterizedType) {
                return nc.withClazz(TypeTree.build(((J.ParameterizedType) nc.getClazz()).getClazz() + "<>").withPrefix(Space.SINGLE_SPACE));
            }
            return nc;
        }
    }

}

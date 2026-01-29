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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

public class MigrateStepExecutionTimestampTypes extends Recipe {

    @Getter
    final String displayName = "Migrate `Date` to `LocalDateTime` for Spring Batch timestamp methods";

    @Getter
    final String description = "In Spring Batch 5.0, `StepExecution` and `JobExecution` timestamp methods " +
            "(`getStartTime()`, `getEndTime()`, `getCreateTime()`, `getLastUpdated()`) " +
            "return `java.time.LocalDateTime` instead of `java.util.Date`. " +
            "This recipe updates variable declarations accordingly.";

    private static final MethodMatcher STEP_GET_START_TIME = new MethodMatcher("org.springframework.batch.core.StepExecution getStartTime()");
    private static final MethodMatcher STEP_GET_END_TIME = new MethodMatcher("org.springframework.batch.core.StepExecution getEndTime()");
    private static final MethodMatcher STEP_GET_LAST_UPDATED = new MethodMatcher("org.springframework.batch.core.StepExecution getLastUpdated()");
    private static final MethodMatcher JOB_GET_START_TIME = new MethodMatcher("org.springframework.batch.core.JobExecution getStartTime()");
    private static final MethodMatcher JOB_GET_END_TIME = new MethodMatcher("org.springframework.batch.core.JobExecution getEndTime()");
    private static final MethodMatcher JOB_GET_CREATE_TIME = new MethodMatcher("org.springframework.batch.core.JobExecution getCreateTime()");
    private static final MethodMatcher JOB_GET_LAST_UPDATED = new MethodMatcher("org.springframework.batch.core.JobExecution getLastUpdated()");

    private static final JavaType.FullyQualified LOCAL_DATE_TIME_TYPE =
            JavaType.ShallowClass.build("java.time.LocalDateTime");

    private static boolean isTimestampMethod(J.MethodInvocation mi) {
        return Stream.of(STEP_GET_START_TIME, STEP_GET_END_TIME, STEP_GET_LAST_UPDATED,
                        JOB_GET_START_TIME, JOB_GET_END_TIME, JOB_GET_CREATE_TIME, JOB_GET_LAST_UPDATED)
                .anyMatch(matcher -> matcher.matches(mi));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>("org.springframework.batch.core.StepExecution get*()"),
                        new UsesMethod<>("org.springframework.batch.core.JobExecution get*()")
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                        if (c != cu) {
                            boolean dateStillUsed = new JavaIsoVisitor<AtomicBoolean>() {
                                @Override
                                public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations vd, AtomicBoolean found) {
                                    if (TypeUtils.isOfClassType(vd.getType(), "java.util.Date")) {
                                        found.set(true);
                                    }
                                    return vd;
                                }

                                @Override
                                public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                                    if (TypeUtils.isOfClassType(newClass.getType(), "java.util.Date")) {
                                        found.set(true);
                                    }
                                    return super.visitNewClass(newClass, found);
                                }
                            }.reduce(c, new AtomicBoolean(false)).get();

                            if (!dateStillUsed) {
                                doAfterVisit(new RemoveImport<>("java.util.Date", true));
                            }
                        }
                        return c;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                        if (!TypeUtils.isOfClassType(vd.getType(), "java.util.Date")) {
                            return vd;
                        }
                        boolean hasTimestampInit = vd.getVariables().stream()
                                .filter(v -> v.getInitializer() instanceof J.MethodInvocation)
                                .anyMatch(v -> isTimestampMethod((J.MethodInvocation) v.getInitializer()));

                        if (hasTimestampInit && vd.getTypeExpression() != null) {
                            maybeAddImport("java.time.LocalDateTime");
                            J.Identifier typeExpr = new J.Identifier(
                                    Tree.randomId(),
                                    vd.getTypeExpression().getPrefix(),
                                    Markers.EMPTY,
                                    emptyList(),
                                    "LocalDateTime",
                                    LOCAL_DATE_TIME_TYPE,
                                    null
                            );
                            List<J.VariableDeclarations.NamedVariable> updatedVars = new ArrayList<>(vd.getVariables());
                            for (int i = 0; i < updatedVars.size(); i++) {
                                J.VariableDeclarations.NamedVariable v = updatedVars.get(i);
                                if (v.getInitializer() instanceof J.MethodInvocation) {
                                    J.MethodInvocation mi = (J.MethodInvocation) v.getInitializer();
                                    if (isTimestampMethod(mi) && mi.getMethodType() != null) {
                                        mi = mi.withMethodType(mi.getMethodType().withReturnType(LOCAL_DATE_TIME_TYPE));
                                        J.Identifier name = v.getName();
                                        if (name.getFieldType() != null) {
                                            name = name.withFieldType(name.getFieldType().withType(LOCAL_DATE_TIME_TYPE));
                                        }
                                        updatedVars.set(i, v.withName(name).withInitializer(mi).withType(LOCAL_DATE_TIME_TYPE));
                                    }
                                }
                            }
                            return vd
                                    .withVariables(updatedVars)
                                    .withTypeExpression(typeExpr)
                                    .withType(LOCAL_DATE_TIME_TYPE);
                        }
                        return vd;
                    }
                }
        );
    }
}

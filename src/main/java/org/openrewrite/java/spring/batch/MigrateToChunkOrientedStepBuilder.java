/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class MigrateToChunkOrientedStepBuilder extends Recipe {

    private static final String CHUNK_WITH_TRANSACTION_MANAGER = "org.springframework.batch.core.step.builder.StepBuilder chunk(int, org.springframework.transaction.PlatformTransactionManager)";
    private static final MethodMatcher CHUNK_MATCHER = new MethodMatcher(CHUNK_WITH_TRANSACTION_MANAGER);

    private static final String CHUNK_ORIENTED_STEP_BUILDER = "org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder";
    private static final String TASKLET_STEP = "org.springframework.batch.core.step.tasklet.TaskletStep";
    private static final String ASYNC_TASK_EXECUTOR = "org.springframework.core.task.AsyncTaskExecutor";

    // `StepListener` moved package in Spring Batch 6.0; this recipe runs both before and after that relocation
    private static final String[] STEP_LISTENERS = {
            "org.springframework.batch.core.StepListener",
            "org.springframework.batch.core.listener.StepListener"};

    private static final String STEP_BUILDERS = "org.springframework.batch.core.step.builder.*";
    private static final MethodMatcher BUILD_MATCHER = new MethodMatcher(STEP_BUILDERS + " build()");
    private static final MethodMatcher LISTENER_MATCHER = new MethodMatcher(STEP_BUILDERS + " listener(..)");
    private static final MethodMatcher TASK_EXECUTOR_MATCHER = new MethodMatcher(STEP_BUILDERS + " taskExecutor(..)");

    // Methods absent here have no counterpart on `ChunkOrientedStepBuilder`, so chains using them are left alone
    private static final List<MethodMatcher> SUPPORTED_BUILDER_METHODS = Stream.of(
                    "allowStartIfComplete",
                    "build",
                    "faultTolerant",
                    "listener",
                    "observationRegistry",
                    "processor",
                    "reader",
                    "retry",
                    "retryLimit",
                    "skip",
                    "skipLimit",
                    "skipPolicy",
                    "startLimit",
                    "stream",
                    "taskExecutor",
                    "transactionAttribute",
                    "transactionManager",
                    "writer")
            .map(name -> new MethodMatcher(STEP_BUILDERS + " " + name + "(..)"))
            .collect(toList());

    @Getter
    final String displayName = "Migrate to the new chunk-oriented step model";

    @Getter
    final String description = "Spring Batch 6.0 deprecates `StepBuilder.chunk(int, PlatformTransactionManager)` in " +
            "favor of a new chunk-oriented model, where the transaction manager is configured through " +
            "`ChunkOrientedStepBuilder.transactionManager(PlatformTransactionManager)`. Replaces " +
            "`chunk(chunkSize, transactionManager)` with `chunk(chunkSize).transactionManager(transactionManager)`, " +
            "but only where every other method in the builder chain has an equivalent on " +
            "`ChunkOrientedStepBuilder`. The new model dropped the Spring Retry based chunk and retry APIs without a " +
            "drop-in replacement, so chains calling `backOffPolicy`, `retryPolicy`, `retryContextCache`, " +
            "`keyGenerator`, `noRetry`, `noRollback`, `noSkip`, `processorNonTransactional`, " +
            "`readerIsTransactionalQueue`, `chunkOperations`, `stepOperations`, `exceptionHandler`, " +
            "`listener(RetryListener)`, `chunk(CompletionPolicy, PlatformTransactionManager)` or " +
            "`taskExecutor(TaskExecutor)` with a non-async executor are left untouched, and remain a manual migration " +
            "step. See the [Spring Batch 6.0 migration guide](https://github.com/spring-projects/spring-batch/wiki/Spring-Batch-6.0-Migration-Guide#new-chunk-oriented-model-implementation).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(CHUNK_WITH_TRANSACTION_MANAGER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!CHUNK_MATCHER.matches(mi) || !chainSupportsNewModel(getCursor())) {
                    return mi;
                }

                Expression chunkSize = mi.getArguments().get(0);
                Expression transactionManager = mi.getArguments().get(1);
                JavaType.FullyQualified chunkOrientedStepBuilderRaw = JavaType.ShallowClass.build(CHUNK_ORIENTED_STEP_BUILDER);
                JavaType.FullyQualified chunkOrientedStepBuilder = chunkOrientedStepBuilderRaw;
                if (mi.getMethodType() != null && mi.getMethodType().getReturnType() instanceof JavaType.Parameterized) {
                    List<JavaType> typeArgs = ((JavaType.Parameterized) mi.getMethodType().getReturnType()).getTypeParameters();
                    if (!typeArgs.isEmpty()) {
                        chunkOrientedStepBuilder = new JavaType.Parameterized(null, chunkOrientedStepBuilderRaw, typeArgs);
                    }
                }

                JavaType.Method chunkType = mi.getMethodType() == null ? null : mi.getMethodType()
                        .withParameterNames(singletonList("chunkSize"))
                        .withParameterTypes(singletonList(chunkSize.getType()))
                        .withReturnType(chunkOrientedStepBuilder);
                J.MethodInvocation chunk = mi
                        .withArguments(singletonList(chunkSize))
                        .withName(mi.getName().withType(chunkType));

                JavaType.Method transactionManagerType = chunkType == null ? null : chunkType
                        .withDeclaringType(chunkOrientedStepBuilder)
                        .withName("transactionManager")
                        .withParameterNames(singletonList("transactionManager"))
                        .withParameterTypes(singletonList(transactionManager.getType()));
                return mi
                        .withId(Tree.randomId())
                        .withPrefix(Space.EMPTY)
                        .withSelect(chunk)
                        .withName(mi.getName().withSimpleName("transactionManager").withType(transactionManagerType))
                        .withTypeParameters(null)
                        .withArguments(singletonList(transactionManager.withPrefix(Space.EMPTY)));
            }
        });
    }

    // The chain has to end in `build()` so that the widened return type of the new model stays contained
    private static boolean chainSupportsNewModel(Cursor chunkCursor) {
        Cursor cursor = chunkCursor;
        J current = cursor.getValue();
        while (true) {
            Cursor parent = cursor.getParentTreeCursor();
            if (!(parent.getValue() instanceof J.MethodInvocation)) {
                return false;
            }
            J.MethodInvocation next = parent.getValue();
            if (next.getSelect() != current || !isSupportedOnChunkOrientedStepBuilder(next)) {
                return false;
            }
            if (BUILD_MATCHER.matches(next)) {
                return !returnsTaskletStep(parent);
            }
            cursor = parent;
            current = next;
        }
    }

    private static boolean isSupportedOnChunkOrientedStepBuilder(J.MethodInvocation method) {
        if (SUPPORTED_BUILDER_METHODS.stream().noneMatch(matcher -> matcher.matches(method))) {
            return false;
        }
        // `listener(Object)` and `listener(RetryListener)` are silently dropped by the new model
        if (LISTENER_MATCHER.matches(method)) {
            return isArgumentAssignableToAny(method, STEP_LISTENERS);
        }
        // The new model narrowed `taskExecutor(TaskExecutor)` to `taskExecutor(AsyncTaskExecutor)`
        if (TASK_EXECUTOR_MATCHER.matches(method)) {
            return isArgumentAssignableToAny(method, ASYNC_TASK_EXECUTOR);
        }
        return true;
    }

    private static boolean isArgumentAssignableToAny(J.MethodInvocation method, String... fullyQualifiedNames) {
        if (method.getArguments().size() != 1) {
            return false;
        }
        JavaType argumentType = method.getArguments().get(0).getType();
        for (String fullyQualifiedName : fullyQualifiedNames) {
            if (TypeUtils.isAssignableTo(fullyQualifiedName, argumentType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean returnsTaskletStep(Cursor buildCursor) {
        Object parent = buildCursor.getParentTreeCursor().getValue();
        if (parent instanceof J.VariableDeclarations.NamedVariable) {
            return TypeUtils.isOfClassType(((J.VariableDeclarations.NamedVariable) parent).getType(), TASKLET_STEP);
        }
        if (parent instanceof J.Return) {
            J.MethodDeclaration enclosing = buildCursor.firstEnclosing(J.MethodDeclaration.class);
            return enclosing != null && TypeUtils.isOfClassType(enclosing.getType(), TASKLET_STEP);
        }
        if (parent instanceof J.Assignment) {
            return TypeUtils.isOfClassType(((J.Assignment) parent).getType(), TASKLET_STEP);
        }
        if (parent instanceof J.TypeCast) {
            return TypeUtils.isOfClassType(((J.TypeCast) parent).getType(), TASKLET_STEP);
        }
        if (parent instanceof J.MethodInvocation) {
            J.MethodInvocation call = (J.MethodInvocation) parent;
            if (call.getSelect() == buildCursor.getValue() || call.getMethodType() == null) {
                return false;
            }
            int argIndex = call.getArguments().indexOf(buildCursor.getValue());
            List<JavaType> paramTypes = call.getMethodType().getParameterTypes();
            return argIndex >= 0 && argIndex < paramTypes.size() &&
                    TypeUtils.isOfClassType(paramTypes.get(argIndex), TASKLET_STEP);
        }
        if (parent instanceof J.NewClass) {
            J.NewClass newClass = (J.NewClass) parent;
            if (newClass.getArguments() == null || newClass.getConstructorType() == null) {
                return false;
            }
            int argIndex = newClass.getArguments().indexOf(buildCursor.getValue());
            List<JavaType> paramTypes = newClass.getConstructorType().getParameterTypes();
            return argIndex >= 0 && argIndex < paramTypes.size() &&
                    TypeUtils.isOfClassType(paramTypes.get(argIndex), TASKLET_STEP);
        }
        return false;
    }
}

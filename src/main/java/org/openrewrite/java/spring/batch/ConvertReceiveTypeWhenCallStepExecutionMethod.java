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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.java.tree.MethodCall;
import org.openrewrite.java.tree.TypeUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ConvertReceiveTypeWhenCallStepExecutionMethod extends Recipe {

    @Getter
    final String displayName = "Cast `StepExecution` count results to match `int` usages";

    @Getter
    final String description = "Spring Batch 5 changed `StepExecution.get*Count()` from `int` to `long`. " +
            "Adds `(int)` casts where `int` is still expected, rewrites method references to lambdas, " +
            "and adds `(long)` casts in Mockito `thenReturn` stubs.";

    private static final MethodMatcher STEP_COUNT_MATCHER = new MethodMatcher("org.springframework.batch.core.StepExecution get*Count()");
    private static final MethodMatcher WHEN_MATCHER = new MethodMatcher("org.mockito.Mockito when(..)");
    private static final MethodMatcher THEN_RETURN_MATCHER = new MethodMatcher("org.mockito.stubbing.OngoingStubbing thenReturn(..)");
    private static final Set<J.Binary.Type> ARITHMETIC = EnumSet.of(
            J.Binary.Type.Addition, J.Binary.Type.Subtraction, J.Binary.Type.Division, J.Binary.Type.Multiplication, J.Binary.Type.Modulo
    );

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(STEP_COUNT_MATCHER),
                new JavaVisitor<ExecutionContext>() {
                    /**
                     * Do not visit method reference from the Javadoc
                     */
                    @Override
                    protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
                        return new JavadocVisitor<ExecutionContext>(this) {
                            @Override
                            public Javadoc visitReference(Javadoc.Reference reference, ExecutionContext ctx) {
                                return reference;
                            }
                        };
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        // Mockito stub check
                        if (isWhenCount(method)) {
                            return method;
                        }
                        J j = super.visitMethodInvocation(method, ctx);
                        if (!(j instanceof J.MethodInvocation)) {
                            return j;
                        }
                        J.MethodInvocation methodInvocation = (J.MethodInvocation) j;

                        // int count = stepExecution.getXCount(); -> int count = (int) stepExecution.getXCount();
                        if (STEP_COUNT_MATCHER.matches(methodInvocation) && isInt(methodInvocation.getType())) {
                            return expectsInt(getCursor()) ? cast("int", methodInvocation, getCursor().getParentOrThrow()) : methodInvocation;
                        }

                        // Handle only StepExecution mock
                        if (THEN_RETURN_MATCHER.matches(methodInvocation) && isWhenCount(methodInvocation.getSelect())) {
                            return methodInvocation.withArguments(
                                    ListUtils.map(methodInvocation.getArguments(),
                                            arg -> isInt(arg.getType()) ? cast("long", arg, getCursor()) : arg)
                            );
                        }
                        return methodInvocation;
                    }

                    @Override
                    public J visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                        J j = super.visitMemberReference(memberRef, ctx);
                        if (!(j instanceof J.MemberReference)) {
                            return j;
                        }
                        J.MemberReference memberReference = (J.MemberReference) j;
                        JavaType.Method method = memberReference.getMethodType();
                        if (method == null || !shouldConvertToLambda(memberReference, method)) {
                            return memberReference;
                        }
                        return toIntLambda(memberReference, method, getCursor(), ctx);
                    }
                }
        );
    }

    // StepExecution::getXCount where result must be int
    private static boolean shouldConvertToLambda(J.MemberReference memberReference, JavaType.Method method) {
        return STEP_COUNT_MATCHER.matches(memberReference)
                && isInt(method.getReturnType())
                && wantsInt(memberReference.getType())
                && isClassReference(memberReference.getContaining());
    }

    private static J toIntLambda(J.MemberReference memberReference, JavaType.Method method, Cursor cursor, ExecutionContext ctx) {
        String param = VariableNameUtils.generateVariableName(
                "stepExecution", cursor, VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER
        );
        String template = String.format("%s -> (int) %s.%s()", param, param, memberReference.getReference().getSimpleName());

        J.Lambda lambda = JavaTemplate.builder(template)
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-5.+",
                        "spring-batch-infrastructure-5.+"))
                .build()
                .apply(cursor, memberReference.getCoordinates().replace());
        return withMethodType(lambda, method);
    }

    // Get type info for generic calls like map(...)
    private static J.Lambda withMethodType(J.Lambda lambda, JavaType.Method originalMethod) {
        J.TypeCast cast = (J.TypeCast) lambda.getBody();
        J.MethodInvocation call = (J.MethodInvocation) cast.getExpression();
        return lambda.withBody(cast.withExpression(call.withMethodType(originalMethod)));
    }

    // StepExecution::getX has a class name before `::` (no field type).
    private static boolean isClassReference(Expression containing) {
        return containing instanceof J.Identifier && ((J.Identifier) containing).getFieldType() == null;
    }

    private static boolean isWhenCount(@Nullable Expression expression) {
        if (!(expression instanceof J.MethodInvocation) || !WHEN_MATCHER.matches(expression)) {
            return false;
        }
        List<Expression> args = ((J.MethodInvocation) expression).getArguments();
        return !args.isEmpty() && STEP_COUNT_MATCHER.matches(args.get(0));
    }

    // expression -> (int) expression or (long) expression
    private static Expression cast(String type, Expression expression, Cursor parent) {
        return JavaTemplate.builder("(" + type + ") #{any(int)}")
                .build()
                .apply(new Cursor(parent, expression), expression.getCoordinates().replace(), expression)
                .withPrefix(expression.getPrefix());
    }

    // Verify if (int) cast is needed else return false by default
    private static boolean expectsInt(Cursor call) {
        // Handling long total = stepExecution.getXCount() + 1
        Cursor self = call;
        Object parent = self.getParentTreeCursor().getValue();
        while (parent instanceof J.Parentheses || parent instanceof J.Binary && ARITHMETIC.contains(((J.Binary) parent).getOperator())) {
            self = self.getParentTreeCursor();
            parent = self.getParentTreeCursor().getValue();
        }

        // Cast if int count = stepExecution.getXCount(), else skip
        if (parent instanceof J.VariableDeclarations.NamedVariable) {
            return isInt(((J.VariableDeclarations.NamedVariable) parent).getType());
        }

        // If assignment (count = stepExecution.getXCount()) check type before cast
        if (parent instanceof J.Assignment) {
            return isInt(((J.Assignment) parent).getType());
        }

        // return count -> cast if method or lambda returns int
        if (parent instanceof J.Return) {
            Object enclosing = self.getParentTreeCursor().dropParentUntil(t -> t instanceof J.MethodDeclaration || t instanceof J.Lambda).getValue();
            return enclosing instanceof J.Lambda ? wantsInt((((J.Lambda) enclosing).getType())) : isInt(((J.MethodDeclaration) enclosing).getType());
        }

        // If stepExecution.getXCount() is passed as an argument check corresponding parameter type
        if (parent instanceof MethodCall) {
            MethodCall outer = (MethodCall) parent;
            JavaType.Method target = outer.getMethodType();
            int position = outer.getArguments().indexOf((Expression) self.getValue());
            if (target != null && position >= 0 && position < target.getParameterTypes().size()) {
                return isInt(target.getParameterTypes().get(position));
            }
        }

        return false;
    }

    private static boolean wantsInt(@Nullable JavaType type) {
        if (type == null || TypeUtils.isOfClassType(type, "java.util.function.ToLongFunction")
                || TypeUtils.isOfClassType(type, "java.util.function.ToDoubleFunction")) {
            return false;
        }
        if (TypeUtils.isOfClassType(type, "java.util.function.ToIntFunction")) {
            return true;
        }
        if (!(type instanceof JavaType.Parameterized)) {
            return true;
        }
        // The result type is last type argument of Function<T, R>, Supplier<R> or ? extends Long
        List<JavaType> args = ((JavaType.Parameterized) type).getTypeParameters();
        return !args.isEmpty() && !TypeUtils.isAssignableTo("java.lang.Long", args.get(args.size() - 1));
    }

    // int or Integer
    private static boolean isInt(@Nullable JavaType type) {
        return TypeUtils.isOfType(type, JavaType.Primitive.Int) || TypeUtils.isOfClassType(type, "java.lang.Integer");
    }
}

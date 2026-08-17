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
package org.openrewrite.java.spring.data;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.trait.Comments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Data MongoDB 5 changes {@code DefaultMessageListenerContainer}'s {@code SmartLifecycle}
 * default from manual-start ({@code isAutoStartup()} hardcoded {@code false} in 4.x) to
 * automatic-start ({@code autoStartup} defaults {@code true}, with a new {@code setAutoStartup(boolean)}
 * setter). A Spring-managed container that relied on the old default would silently start listening
 * during context startup after migration, changing application sequencing without any corresponding
 * source change.
 */
public class PreserveMongoListenerContainerAutoStartup extends Recipe {

    private static final String CONTAINER_TYPE = "org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer";
    private static final AnnotationMatcher BEAN_ANNOTATION_MATCHER = new AnnotationMatcher("@org.springframework.context.annotation.Bean");
    private static final String DEFAULT_VARIABLE_NAME = "container";
    private static final String UNSUPPORTED_MESSAGE = "Unable to verify this DefaultMessageListenerContainer return path; " +
            "preserve startup behavior manually if required.";
    private static final String ACTIONS = "PRESERVE_MONGO_LISTENER_ACTIONS";
    private static final String UNSUPPORTED_RETURNS = "PRESERVE_MONGO_LISTENER_UNSUPPORTED_RETURNS";

    @Getter
    final String displayName = "Preserve `DefaultMessageListenerContainer` manual-start behavior";

    @Getter
    final String description = "Spring Data MongoDB 5 changes `DefaultMessageListenerContainer`'s default `autoStartup` " +
            "from `false` to `true`. For Spring-managed beans, preserves the previous behavior on return paths whose " +
            "container provenance can be verified locally, including direct construction, local variables, same-block " +
            "field assignment, and simple one-hop helper methods in the same class. Marks unsupported return paths for " +
            "manual review rather than guessing.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(CONTAINER_TYPE, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (!isBeanMethod(method)) {
                    return super.visitMethodDeclaration(method, ctx);
                }

                J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                MethodBodyAnalysis analysis = analyze(method, enclosingClass);
                ReturnClassifier classifier = new ReturnClassifier(method, enclosingClass, analysis);
                for (J.Return theReturn : analysis.returns) {
                    classifier.classify(theReturn);
                }

                if (!classifier.actions.isEmpty()) {
                    getCursor().putMessage(ACTIONS, classifier.actions);
                }
                if (!classifier.unsupported.isEmpty()) {
                    getCursor().putMessage(UNSUPPORTED_RETURNS, classifier.unsupported);
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.Return visitReturn(J.Return _return, ExecutionContext ctx) {
                J.Return r = super.visitReturn(_return, ctx);
                Set<UUID> unsupported = getCursor().getNearestMessage(UNSUPPORTED_RETURNS);
                if (unsupported != null && unsupported.contains(r.getId())) {
                    return Comments.of(getCursor()).comment(" " + UNSUPPORTED_MESSAGE);
                }
                return r;
            }

            // A return is always the last statement of its immediately enclosing block. Apply the
            // per-return action at that block boundary so the transformation works for nested returns
            // without violating JavaIsoVisitor's J.Return -> J.Return contract.
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                J.Return r = lastReturnOf(b);
                if (r == null) {
                    return b;
                }

                Map<UUID, Action> actions = getCursor().getNearestMessage(ACTIONS);
                Action action = actions == null ? null : actions.get(r.getId());
                if (action == null) {
                    return b;
                }

                return action.existingVariable != null ?
                        insertSetAutoStartupBefore(b, r, action.existingVariable, ctx) :
                        declareConfiguredVariableReturn(b, r, action.newVariableName, ctx);
            }

            private J.Block insertSetAutoStartupBefore(J.Block b, J.Return r, Expression existingVariable, ExecutionContext ctx) {
                return JavaTemplate.builder("#{any(" + CONTAINER_TYPE + ")}.setAutoStartup(false);")
                        .contextSensitive()
                        .javaParser(javaParser(ctx))
                        .build()
                        .apply(updateCursor(b), r.getCoordinates().before(), existingVariable);
            }

            private J.Block declareConfiguredVariableReturn(J.Block b, J.Return r, @Nullable String variableName, ExecutionContext ctx) {
                Expression returnExpression = r.getExpression();
                if (returnExpression == null || variableName == null) {
                    return b;
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(returnExpression.getType());
                String simpleName = type == null ? "DefaultMessageListenerContainer" : type.getClassName();
                return JavaTemplate.builder(
                                simpleName + " " + variableName + " = #{any(" + CONTAINER_TYPE + ")};\n" +
                                variableName + ".setAutoStartup(false);\n" +
                                "return " + variableName + ";")
                        .contextSensitive()
                        .javaParser(javaParser(ctx))
                        .build()
                        .apply(updateCursor(b), r.getCoordinates().replace(), returnExpression);
            }

            private boolean isBeanMethod(J.MethodDeclaration method) {
                return method.getBody() != null &&
                        method.getReturnTypeExpression() != null &&
                        service(AnnotationService.class).matches(getCursor(), BEAN_ANNOTATION_MATCHER);
            }
        });
    }

    private static JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
        return JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-data-mongodb-5.+", "spring-context-6.+");
    }

    private static J.@Nullable Return lastReturnOf(J.Block block) {
        if (block.getStatements().isEmpty()) {
            return null;
        }
        Statement last = block.getStatements().get(block.getStatements().size() - 1);
        return last instanceof J.Return ? (J.Return) last : null;
    }

    private static boolean isThis(Expression expression) {
        return expression instanceof J.Identifier && "this".equals(((J.Identifier) expression).getSimpleName());
    }

    // Resolves a call to at most one hop: an unqualified (or explicit `this.`) same-class method
    // whose own body has a single return. Helper bodies are analyzed without an enclosing class
    // (enclosingClass == null), so a helper's own calls to further helpers are never chased, keeping
    // the search one-hop deep.
    private static HelperResult inspectOneHopHelper(J.@Nullable ClassDeclaration enclosingClass,
                                                     J.MethodDeclaration callerMethod,
                                                     J.MethodInvocation invocation) {
        if (invocation.getMethodType() == null ||
                (invocation.getSelect() != null && !isThis(invocation.getSelect())) ||
                enclosingClass == null) {
            return unresolvedHelperResult(invocation);
        }

        J.MethodDeclaration helper = findHelperMethod(enclosingClass, callerMethod, invocation.getMethodType());
        if (helper == null || helper.getBody() == null) {
            return unresolvedHelperResult(invocation);
        }

        MethodBodyAnalysis helperAnalysis = analyze(helper, null);
        if (helperAnalysis.returns.size() != 1) {
            return HelperResult.UNSUPPORTED;
        }
        return classifyHelperReturn(helperAnalysis, helperAnalysis.returns.iterator().next(), invocation);
    }

    // Without a resolved helper, all we can tell is whether the call itself looks like a container:
    // if so, the return path can't be verified locally, otherwise it's simply irrelevant to this recipe.
    private static HelperResult unresolvedHelperResult(J.MethodInvocation invocation) {
        return isContainerType(invocation.getType()) ? HelperResult.UNSUPPORTED : HelperResult.NOT_CONTAINER;
    }

    private static J.@Nullable MethodDeclaration findHelperMethod(J.ClassDeclaration enclosingClass,
                                                                   J.MethodDeclaration callerMethod,
                                                                   JavaType.Method invokedSignature) {
        for (Statement statement : enclosingClass.getBody().getStatements()) {
            if (!(statement instanceof J.MethodDeclaration)) {
                continue;
            }
            J.MethodDeclaration candidate = (J.MethodDeclaration) statement;
            JavaType.Method candidateSignature = candidate.getMethodType();
            if (candidate == callerMethod || candidateSignature == null) {
                continue;
            }
            if (invokedSignature.equals(candidateSignature)) {
                return candidate;
            }
        }
        return null;
    }

    private static HelperResult classifyHelperReturn(MethodBodyAnalysis helperAnalysis, J.Return helperReturn,
                                                      J.MethodInvocation invocation) {
        Expression returned = helperReturn.getExpression();
        if (returned == null) {
            return HelperResult.NOT_CONTAINER;
        }
        boolean invocationIsContainer = isContainerType(invocation.getType());
        boolean returnedIsContainer = isContainerType(returned.getType());

        if (returned instanceof J.NewClass && returnedIsContainer) {
            return invocationIsContainer ? HelperResult.UNCONFIGURED : HelperResult.UNSUPPORTED;
        }

        JavaType.Variable variable = variableOf(returned);
        if (variable == null) {
            return returnedIsContainer ? HelperResult.UNSUPPORTED : HelperResult.NOT_CONTAINER;
        }
        VariableState state = helperAnalysis.stateAt(helperReturn, variable);
        if (state == VariableState.CONFIGURED) {
            return HelperResult.CONFIGURED;
        }
        if (state == VariableState.UNCONFIGURED) {
            return invocationIsContainer && returnedIsContainer ?
                    HelperResult.UNCONFIGURED : HelperResult.UNSUPPORTED;
        }
        return returnedIsContainer || helperAnalysis.hasContainerDefinitionBefore(helperReturn, variable) ?
                HelperResult.UNSUPPORTED : HelperResult.NOT_CONTAINER;
    }

    private static boolean isContainerType(@Nullable JavaType type) {
        return TypeUtils.isAssignableTo(CONTAINER_TYPE, type);
    }

    // isBeanMethod() guarantees a non-null return type expression before this is ever called; the
    // null check here is purely to keep the accessor itself null-safe.
    private static @Nullable JavaType beanReturnType(J.MethodDeclaration beanMethod) {
        return beanMethod.getReturnTypeExpression() == null ? null : beanMethod.getReturnTypeExpression().getType();
    }

    private static JavaType.@Nullable Variable variableOf(Expression expression) {
        if (expression instanceof J.Identifier) {
            return ((J.Identifier) expression).getFieldType();
        }
        if (expression instanceof J.FieldAccess) {
            return ((J.FieldAccess) expression).getName().getFieldType();
        }
        return null;
    }

    private static String uniqueName(Set<String> taken) {
        if (!taken.contains(DEFAULT_VARIABLE_NAME)) {
            return DEFAULT_VARIABLE_NAME;
        }
        int suffix = 2;
        while (taken.contains(DEFAULT_VARIABLE_NAME + suffix)) {
            suffix++;
        }
        return DEFAULT_VARIABLE_NAME + suffix;
    }

    private enum HelperResult {
        UNCONFIGURED,
        CONFIGURED,
        UNSUPPORTED,
        NOT_CONTAINER
    }

    private enum VariableState {
        UNCONFIGURED,
        CONFIGURED,
        UNSUPPORTED,
        AMBIGUOUS
    }

    private enum EventType {
        NEW_CONTAINER,
        OTHER_ASSIGNMENT,
        CONFIGURATION
    }

    private static final class Action {
        final @Nullable Expression existingVariable;
        final @Nullable String newVariableName;

        Action(@Nullable Expression existingVariable, @Nullable String newVariableName) {
            this.existingVariable = existingVariable;
            this.newVariableName = newVariableName;
        }
    }

    // Classifies every return in one @Bean method's body, accumulating a template Action for each
    // locally verifiable path and flagging the rest as unsupported.
    private static final class ReturnClassifier {
        private final J.MethodDeclaration beanMethod;
        private final J.@Nullable ClassDeclaration enclosingClass;
        private final MethodBodyAnalysis analysis;
        final Map<UUID, Action> actions = new HashMap<>();
        final Set<UUID> unsupported = new HashSet<>();

        ReturnClassifier(J.MethodDeclaration beanMethod, J.@Nullable ClassDeclaration enclosingClass, MethodBodyAnalysis analysis) {
            this.beanMethod = beanMethod;
            this.enclosingClass = enclosingClass;
            this.analysis = analysis;
        }

        void classify(J.Return theReturn) {
            Expression returned = theReturn.getExpression();
            if (returned == null) {
                return;
            }

            if (returned instanceof J.NewClass && TypeUtils.isAssignableTo(CONTAINER_TYPE, returned.getType())) {
                addActionOrDiagnostic(theReturn, capture());
                return;
            }

            JavaType.Variable variable = variableOf(returned);
            if (variable != null) {
                classifyVariableReturn(theReturn, returned, variable);
                return;
            }

            if (returned instanceof J.MethodInvocation) {
                classifyHelperCallReturn(theReturn, (J.MethodInvocation) returned);
                return;
            }

            if (isContainerType(returned.getType()) || isContainerType(beanReturnType(beanMethod))) {
                unsupported.add(theReturn.getId());
            }
        }

        private void classifyVariableReturn(J.Return theReturn, Expression returned, JavaType.Variable variable) {
            VariableState state = analysis.stateAt(theReturn, variable);
            if (state == VariableState.CONFIGURED) {
                return;
            }
            if (state == VariableState.UNCONFIGURED) {
                if (isContainerType(returned.getType())) {
                    addActionOrDiagnostic(theReturn, new Action(returned, null));
                } else {
                    unsupported.add(theReturn.getId());
                }
                return;
            }
            if (isContainerType(returned.getType()) ||
                    isContainerType(beanReturnType(beanMethod)) ||
                    analysis.hasContainerDefinitionBefore(theReturn, variable)) {
                unsupported.add(theReturn.getId());
            }
        }

        private void classifyHelperCallReturn(J.Return theReturn, J.MethodInvocation returned) {
            HelperResult helperResult = inspectOneHopHelper(enclosingClass, beanMethod, returned);
            if (helperResult == HelperResult.UNCONFIGURED) {
                addActionOrDiagnostic(theReturn, capture());
            } else if (helperResult == HelperResult.UNSUPPORTED) {
                unsupported.add(theReturn.getId());
            }
        }

        private void addActionOrDiagnostic(J.Return theReturn, Action action) {
            if (analysis.transformableReturns.contains(theReturn.getId())) {
                actions.put(theReturn.getId(), action);
            } else {
                unsupported.add(theReturn.getId());
            }
        }

        private Action capture() {
            return new Action(null, uniqueName(analysis.declaredNames));
        }
    }

    private static final class MethodBodyAnalysis {
        final Set<J.Return> returns = new HashSet<>();
        final Map<UUID, ReturnContext> returnContexts = new HashMap<>();
        final List<VariableEvent> events = new ArrayList<>();
        final Set<UUID> transformableReturns = new HashSet<>();
        final Set<String> declaredNames = new HashSet<>();

        boolean hasContainerDefinitionBefore(J.Return theReturn, JavaType.Variable variable) {
            ReturnContext returnContext = returnContexts.get(theReturn.getId());
            if (returnContext == null) {
                return false;
            }
            for (VariableEvent event : events) {
                if (event.order < returnContext.order && event.variable.equals(variable) &&
                        event.type == EventType.NEW_CONTAINER) {
                    return true;
                }
            }
            return false;
        }

        VariableState stateAt(J.Return theReturn, JavaType.Variable variable) {
            ReturnContext returnContext = returnContexts.get(theReturn.getId());
            if (returnContext == null) {
                return VariableState.UNSUPPORTED;
            }

            VariableEvent definition = latestDefinitionBefore(returnContext, variable);
            if (definition == null || definition.type != EventType.NEW_CONTAINER) {
                return VariableState.UNSUPPORTED;
            }
            return configurationStateAfter(definition, returnContext, variable);
        }

        // The most recent (non-configuration) event that defines the variable's value, guaranteed to
        // have happened before the return and reachable from it - i.e., what the variable "is" there.
        private @Nullable VariableEvent latestDefinitionBefore(ReturnContext returnContext, JavaType.Variable variable) {
            VariableEvent definition = null;
            for (VariableEvent event : events) {
                if (event.order >= returnContext.order || !event.variable.equals(variable) ||
                        event.type == EventType.CONFIGURATION || event.isNotGuaranteedFor(returnContext)) {
                    continue;
                }
                if (definition == null || event.order > definition.order) {
                    definition = event;
                }
            }
            return definition;
        }

        // Whether setAutoStartup(...) was guaranteed to run on the variable between its definition
        // and the return. An event on a path not guaranteed to reach the return makes the answer
        // impossible to determine locally, hence AMBIGUOUS rather than assuming either way.
        private VariableState configurationStateAfter(VariableEvent definition, ReturnContext returnContext,
                                                       JavaType.Variable variable) {
            boolean configured = false;
            for (VariableEvent event : events) {
                if (event.order <= definition.order || event.order >= returnContext.order ||
                        !event.variable.equals(variable)) {
                    continue;
                }
                if (event.isNotGuaranteedFor(returnContext)) {
                    return VariableState.AMBIGUOUS;
                }
                if (event.type == EventType.CONFIGURATION) {
                    configured = true;
                }
            }
            return configured ? VariableState.CONFIGURED : VariableState.UNCONFIGURED;
        }
    }

    private static final class ReturnContext {
        final int order;
        final Set<UUID> blockAncestors;
        final Set<UUID> controlAncestors;

        ReturnContext(int order, Set<UUID> blockAncestors, Set<UUID> controlAncestors) {
            this.order = order;
            this.blockAncestors = blockAncestors;
            this.controlAncestors = controlAncestors;
        }
    }

    private static final class VariableEvent {
        final JavaType.Variable variable;
        final EventType type;
        final int order;
        final @Nullable UUID nearestBlock;
        final Set<UUID> controlAncestors;

        VariableEvent(JavaType.Variable variable, EventType type, int order, @Nullable UUID nearestBlock,
                      Set<UUID> controlAncestors) {
            this.variable = variable;
            this.type = type;
            this.order = order;
            this.nearestBlock = nearestBlock;
            this.controlAncestors = controlAncestors;
        }

        boolean isNotGuaranteedFor(ReturnContext returnContext) {
            return nearestBlock == null || !returnContext.blockAncestors.contains(nearestBlock) ||
                    !returnContext.controlAncestors.containsAll(controlAncestors);
        }
    }

    private static MethodBodyAnalysis analyze(J.MethodDeclaration method, J.@Nullable ClassDeclaration enclosingClass) {
        MethodBodyAnalysis analysis = new MethodBodyAnalysis();
        for (Statement parameter : method.getParameters()) {
            if (parameter instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) parameter).getVariables()) {
                    analysis.declaredNames.add(variable.getSimpleName());
                }
            }
        }
        new BodyAnalyzer(method, enclosingClass).visit(method, analysis);
        return analysis;
    }

    // Records every return and container-variable event (definition, reassignment, configuration) in
    // one @Bean method's body, skipping lambdas, anonymous classes, and nested methods - those are
    // different invocation contexts.
    private static final class BodyAnalyzer extends JavaIsoVisitor<MethodBodyAnalysis> {
        private final J.MethodDeclaration method;
        private final J.@Nullable ClassDeclaration enclosingClass;
        private int order;

        BodyAnalyzer(J.MethodDeclaration method, J.@Nullable ClassDeclaration enclosingClass) {
            this.method = method;
            this.enclosingClass = enclosingClass;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration m, MethodBodyAnalysis a) {
            return m == method ? super.visitMethodDeclaration(m, a) : m;
        }

        @Override
        public J.Lambda visitLambda(J.Lambda lambda, MethodBodyAnalysis a) {
            return lambda;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, MethodBodyAnalysis a) {
            return newClass.getBody() != null ? newClass : super.visitNewClass(newClass, a);
        }

        @Override
        public J.Block visitBlock(J.Block block, MethodBodyAnalysis a) {
            J.Block b = super.visitBlock(block, a);
            if (b.getStatements().isEmpty()) {
                return b;
            }
            Statement last = b.getStatements().get(b.getStatements().size() - 1);
            if (last instanceof J.Return) {
                a.transformableReturns.add(last.getId());
            }
            return b;
        }

        @Override
        public J.Return visitReturn(J.Return _return, MethodBodyAnalysis a) {
            a.returns.add(_return);
            Location location = location();
            a.returnContexts.put(_return.getId(), new ReturnContext(
                    order++, location.blockAncestors, location.controlAncestors));
            return super.visitReturn(_return, a);
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, MethodBodyAnalysis a) {
            a.declaredNames.add(variable.getSimpleName());
            Expression initializer = variable.getInitializer();
            JavaType.Variable variableType = variable.getVariableType();
            if (variableType != null && initializer != null) {
                addEvent(a, variableType, classify(initializer));
            }
            return super.visitVariable(variable, a);
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, MethodBodyAnalysis a) {
            JavaType.Variable variable = variableOf(assignment.getVariable());
            if (variable != null) {
                addEvent(a, variable, classify(assignment.getAssignment()));
            }
            return super.visitAssignment(assignment, a);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, MethodBodyAnalysis a) {
            if ("setAutoStartup".equals(invocation.getSimpleName()) &&
                    invocation.getArguments().size() == 1 && invocation.getSelect() != null) {
                JavaType.Variable variable = variableOf(invocation.getSelect());
                if (variable != null) {
                    addEvent(a, variable, EventType.CONFIGURATION);
                }
            }
            return super.visitMethodInvocation(invocation, a);
        }

        // A value counts as a fresh, unconfigured container either when it's constructed directly
        // or when it's a call to a same-class one-hop helper that itself produces one.
        private EventType classify(Expression value) {
            if (value instanceof J.NewClass && isContainerType(value.getType())) {
                return EventType.NEW_CONTAINER;
            }
            if (value instanceof J.MethodInvocation &&
                    inspectOneHopHelper(enclosingClass, method, (J.MethodInvocation) value) == HelperResult.UNCONFIGURED) {
                return EventType.NEW_CONTAINER;
            }
            return EventType.OTHER_ASSIGNMENT;
        }

        private void addEvent(MethodBodyAnalysis a, JavaType.Variable variable, EventType type) {
            Location location = location();
            a.events.add(new VariableEvent(variable, type, order++, location.nearestBlock,
                    location.controlAncestors));
        }

        private Location location() {
            Set<UUID> blockAncestors = new HashSet<>();
            Set<UUID> controlAncestors = new HashSet<>();
            UUID nearestBlock = null;
            for (Cursor cursor = getCursor(); cursor != null; cursor = cursor.getParent()) {
                Object value = cursor.getValue();
                if (value instanceof J.Block) {
                    UUID blockId = ((J.Block) value).getId();
                    blockAncestors.add(blockId);
                    if (nearestBlock == null) {
                        nearestBlock = blockId;
                    }
                } else if (isControlFlow(value)) {
                    controlAncestors.add(((J) value).getId());
                }
            }
            return new Location(nearestBlock, blockAncestors, controlAncestors);
        }

        private boolean isControlFlow(Object value) {
            return value instanceof J.If ||
                    value instanceof J.ForLoop ||
                    value instanceof J.ForEachLoop ||
                    value instanceof J.WhileLoop ||
                    value instanceof J.DoWhileLoop ||
                    value instanceof J.Switch ||
                    value instanceof J.Try;
        }
    }

    private static final class Location {
        final @Nullable UUID nearestBlock;
        final Set<UUID> blockAncestors;
        final Set<UUID> controlAncestors;

        Location(@Nullable UUID nearestBlock, Set<UUID> blockAncestors, Set<UUID> controlAncestors) {
            this.nearestBlock = nearestBlock;
            this.blockAncestors = blockAncestors;
            this.controlAncestors = controlAncestors;
        }
    }
}

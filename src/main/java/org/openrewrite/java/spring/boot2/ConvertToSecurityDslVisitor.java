/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.Markup;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor
public class ConvertToSecurityDslVisitor<P> extends JavaIsoVisitor<P> {

    private static final String MSG_FLATTEN_CHAIN = "http-security-dsl-flatten-invocation-chain";
    private static final String MSG_TOP_INVOCATION = "top-method-invocation";

    private static final String FQN_CUSTOMIZER = "org.springframework.security.config.Customizer";
    private static final JavaType.FullyQualified CUSTOMIZER_SHALLOW_TYPE = JavaType.ShallowClass.build(FQN_CUSTOMIZER);

    private static final MethodMatcher XSS_PROTECTION_ENABLED = new MethodMatcher("org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.XXssConfig xssProtectionEnabled(boolean)");

    private final String securityFqn;
    private final Collection<String> convertableMethods;

    /**
     * Optionally used to determine the behavior for any convertableMethods which have an argument.
     * Each key should be a method name from convertableMethods.
     * A non-null value will be used to create a new methodInvocation with that name,
     * and the existing arg will be moved to that new methodInvocation.
     * A null value will keep the existing argument in the converted method.
     */
    private final Map<String, String> argReplacements;

    /**
     * Optionally used to specify replacement method names if they do not match the original method names
     */
    private final Map<String, String> methodRenames;

    public ConvertToSecurityDslVisitor(String securityFqn, Collection<String> convertableMethods) {
        this(securityFqn, convertableMethods, new HashMap<>());
    }

    public ConvertToSecurityDslVisitor(String securityFqn, Collection<String> convertableMethods,
                                       Map<String, String> argReplacements) {
        this(securityFqn, convertableMethods, argReplacements, new HashMap<>());
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation initialMethod, P executionContext) {
        J.MethodInvocation method = super.visitMethodInvocation(initialMethod, executionContext);
        if (isApplicableMethod(method) && !isResultOfConvertedChainUsed()) {
            J.MethodInvocation m = method;
            method = createDesiredReplacement(method)
                    .map(newMethodType -> {
                        List<J.MethodInvocation> chain = computeAndMarkChain();
                        boolean keepArg = keepArg(m.getSimpleName());
                        String paramName = keepArg ? "configurer" : generateParamNameFromMethodName(m.getSimpleName());
                        return m
                                .withMethodType(newMethodType)
                                .withName(m.getName().withSimpleName(newMethodType.getName()).withType(newMethodType))
                                .withArguments(ListUtils.concat(
                                                keepArg ? m.getArguments().get(0) : null,
                                                singletonList(chain.isEmpty() ?
                                                        createDefaultsCall() :
                                                        createLambdaParam(paramName, requireNonNull(m.getMethodType()).getReturnType(), chain))
                                        )
                                );
                    })
                    .orElse(method);
        }
        Boolean msg = getCursor().pollMessage(MSG_FLATTEN_CHAIN);
        if (Boolean.TRUE.equals(msg)) {
            method = requireNonNull(method.getSelect())
                    .withPrefix(method.getPrefix())
                    .withComments(method.getComments());
        }
        // Auto-format the top invocation call if anything has changed down the tree
        if (initialMethod != method && nextCallInChain(getCursor()) == null) {
            method = autoFormat(method, executionContext);
        }
        return method;
    }

    private static String generateParamNameFromMethodName(String n) {
        int i = n.length() - 1;
        //noinspection StatementWithEmptyBody
        for (; i >= 0 && Character.isLowerCase(n.charAt(i)); i--) {
        }
        if (i >= 0) {
            return StringUtils.uncapitalize(i == 0 ? n : n.substring(i));
        }
        return n;
    }

    private J.Lambda createLambdaParam(String paramName, JavaType paramType, List<J.MethodInvocation> chain) {
        // like the parser, declare the parameter of `name -> ...` as a variable with no type expression
        J.MethodDeclaration enclosingMethod = getCursor().firstEnclosing(J.MethodDeclaration.class);
        JavaType.Variable variableType = new JavaType.Variable(null, 0, paramName,
                enclosingMethod == null ? null : enclosingMethod.getMethodType(), paramType, null);
        J.VariableDeclarations param = new J.VariableDeclarations(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                emptyList(), emptyList(), null, null,
                singletonList(new JRightPadded<>(new J.VariableDeclarations.NamedVariable(Tree.randomId(), Space.EMPTY,
                        Markers.EMPTY, createIdentifier(variableType), emptyList(), null, variableType),
                        Space.EMPTY, Markers.EMPTY)));
        J.MethodInvocation body = unfoldMethodInvocationChain(createIdentifier(variableType), chain);
        return new J.Lambda(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                new J.Lambda.Parameters(Tree.randomId(), Space.EMPTY, Markers.EMPTY, false, singletonList(new JRightPadded<>(param, Space.EMPTY, Markers.EMPTY))),
                Space.build(" ", emptyList()),
                body,
                JavaType.Primitive.Void
        );
    }

    private J.Identifier createIdentifier(JavaType.Variable variableType) {
        return new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), variableType.getName(),
                variableType.getType(), variableType);
    }

    private J.MethodInvocation unfoldMethodInvocationChain(J.Identifier core, List<J.MethodInvocation> chain) {
        Expression select = core;
        J.MethodInvocation invocation = null;
        for (J.MethodInvocation inv : chain) {
            invocation = inv.withSelect(select);
            if (XSS_PROTECTION_ENABLED.matches(invocation)) {
                if (J.Literal.isLiteralValue(invocation.getArguments().get(0), false)) {
                    invocation = invocation.withName(invocation.getName().withSimpleName("disable")).withArguments(null);
                    JavaType.Method methodType = invocation.getMethodType();
                    if (methodType != null) {
                        methodType = methodType.withParameterNames(emptyList()).withParameterTypes(emptyList());
                        invocation = invocation.withMethodType(methodType).withName(invocation.getName().withType(methodType));
                    }
                } else {
                    // Enabled by default; but returning `null` will cause issues, so we use `and()` as a placeholder
                    invocation = invocation.withName(invocation.getName().withSimpleName("and")).withArguments(null);
                    JavaType.Method methodType = invocation.getMethodType();
                    if (methodType != null) {
                        methodType = methodType.withParameterNames(emptyList()).withParameterTypes(emptyList());
                        invocation = invocation.withMethodType(methodType).withName(invocation.getName().withType(methodType));
                    }
                }
            }
            select = invocation;
        }
        // Check if top-level invocation to remove the prefix as the prefix is space before the root call, i.e. before httpSecurity identifier. We don't want to have inside the lambda
        if (requireNonNull(invocation).getMarkers().getMarkers().stream().filter(Markup.Info.class::isInstance).map(Markup.Info.class::cast).anyMatch(marker -> MSG_TOP_INVOCATION.equals(marker.getMessage()))) {
            invocation = invocation
                    .withMarkers(invocation.getMarkers().removeByType(Markup.Info.class))
                    .withPrefix(Space.EMPTY);
        }
        return invocation;
    }

    private boolean isApplicableMethod(J.MethodInvocation m) {
        JavaType.Method type = m.getMethodType();
        if (type != null) {
            JavaType.FullyQualified declaringType = type.getDeclaringType();
            // Check if method already has a lambda argument (already transformed in a previous cycle)
            List<Expression> args = m.getArguments();
            if (!args.isEmpty() && !(args.get(0) instanceof J.Empty)) {
                Expression firstArg = args.get(0);
                if (firstArg instanceof J.Lambda) {
                    // Already has a lambda argument, skip transformation
                    return false;
                }
            }
            return securityFqn.equals(declaringType.getFullyQualifiedName()) &&
                    (type.getParameterTypes().isEmpty() || hasHandleableArg(m)) &&
                    convertableMethods.contains(m.getSimpleName());
        }
        return false;
    }

    private boolean hasHandleableArg(J.MethodInvocation m) {
        return argReplacements.containsKey(m.getSimpleName()) &&
                m.getMethodType() != null &&
                m.getMethodType().getParameterTypes().size() == 1 &&
                !TypeUtils.isAssignableTo(FQN_CUSTOMIZER, m.getMethodType().getParameterTypes().get(0));
    }

    private Optional<JavaType.Method> createDesiredReplacement(J.MethodInvocation m) {
        JavaType.Method methodType = m.getMethodType();
        if (methodType == null) {
            return Optional.empty();
        }
        JavaType.Parameterized customizerArgType = new JavaType.Parameterized(null,
                CUSTOMIZER_SHALLOW_TYPE, singletonList(methodType.getReturnType()));
        boolean keepArg = keepArg(m.getSimpleName());
        List<String> paramNames = keepArg ? ListUtils.concat(methodType.getParameterNames(), "arg1") :
                singletonList("arg0");
        List<JavaType> paramTypes = keepArg ? ListUtils.concat(methodType.getParameterTypes(), customizerArgType) :
                singletonList(customizerArgType);
        return Optional.of(methodType.withReturnType(methodType.getDeclaringType())
                .withName(methodRenames.getOrDefault(methodType.getName(), methodType.getName()))
                .withParameterNames(paramNames)
                .withParameterTypes(paramTypes)
        );
    }

    private boolean keepArg(String methodName) {
        return argReplacements.containsKey(methodName) && argReplacements.get(methodName) == null;
    }

    private Optional<JavaType.Method> createDesiredReplacementForArg(J.MethodInvocation m) {
        JavaType.Method methodType = m.getMethodType();
        if (methodType == null || !hasHandleableArg(m) || keepArg(m.getSimpleName()) || !(methodType.getReturnType() instanceof JavaType.Class)) {
            return Optional.empty();
        }
        return Optional.of(
                methodType.withName(argReplacements.get(m.getSimpleName()))
                        .withDeclaringType((JavaType.FullyQualified) methodType.getReturnType())
        );
    }

    // this method is unused in this repo, but, useful in Spring Tool Suite integration
    @SuppressWarnings("unused")
    public boolean isApplicableTopLevelMethodInvocation(J.MethodInvocation m) {
        if (isApplicableMethod(m)) {
            return true;
        }
        if (m.getSelect() instanceof J.MethodInvocation) {
            return isApplicableTopLevelMethodInvocation((J.MethodInvocation) m.getSelect());
        }
        return false;
    }

    private boolean isApplicableCallCursor(@Nullable Cursor c) {
        if (c == null) {
            return false;
        }
        J.MethodInvocation inv = c.getValue();
        return !isAndMethod(inv) && !isDisableMethod(inv);
    }

    private static @Nullable Cursor nextCallInChain(Cursor call) {
        Cursor parent = call.getParentTreeCursor();
        if (parent.getValue() instanceof J.MethodInvocation) {
            Expression select = ((J.MethodInvocation) parent.getValue()).getSelect();
            if (select != null && select.isScope(call.getValue())) {
                return parent;
            }
        }
        return null;
    }

    private List<J.MethodInvocation> computeAndMarkChain() {
        List<J.MethodInvocation> chain = new ArrayList<>();
        Cursor cursor = getCursor();
        J.MethodInvocation initialMethodInvocation = cursor.getValue();
        createDesiredReplacementForArg(initialMethodInvocation).ifPresent(methodType ->
                chain.add(initialMethodInvocation.withName(
                        initialMethodInvocation.getName().withType(methodType).withSimpleName(methodType.getName()))));
        cursor = nextCallInChain(cursor);
        for (; isApplicableCallCursor(cursor); cursor = nextCallInChain(cursor)) {
            cursor.putMessage(MSG_FLATTEN_CHAIN, true);
            chain.add(cursor.getValue());
        }
        if (cursor != null) {
            if (isAndMethod(cursor.getValue())) {
                cursor.putMessage(MSG_FLATTEN_CHAIN, true);
                cursor = nextCallInChain(cursor);
            } else if (isDisableMethod(cursor.getValue())) {
                cursor.putMessage(MSG_FLATTEN_CHAIN, true);
                chain.add(cursor.getValue());
                cursor = nextCallInChain(cursor);
            }
        }
        if (chain.isEmpty()) {
            return emptyList();
        }
        if (cursor == null) {
            // top invocation is at the end of the chain - mark it. We'd need to strip off prefix from this invocation later
            J.MethodInvocation topInvocation = chain.remove(chain.size() - 1);
            // removed above, now add it back with the marker
            chain.add(topInvocation.withMarkers(topInvocation.getMarkers().addIfAbsent(new Markup.Info(Tree.randomId(), MSG_TOP_INVOCATION, null))));
        }
        return chain;
    }

    /**
     * Converting a configurer call plus the calls chained after it into the lambda form changes the type of the
     * resulting expression from the configurer to the security builder, unless the chain is terminated by an
     * `and()` or `disable()` call that already returned the builder. That is only safe when the value of the
     * expression is not used, so a call whose result is returned, assigned or passed on is left unchanged.
     */
    private boolean isResultOfConvertedChainUsed() {
        Cursor topOfChain = getCursor();
        Cursor next = nextCallInChain(topOfChain);
        for (; isApplicableCallCursor(next); next = nextCallInChain(next)) {
            topOfChain = next;
        }
        if (next != null) {
            // terminated by `and()` or `disable()`, so the converted expression still evaluates to the builder
            return false;
        }
        Object enclosing = topOfChain.getParentTreeCursor().getValue();
        return !(enclosing instanceof J.Block ||
                 enclosing instanceof J.Lambda && isVoidLambda((J.Lambda) enclosing));
    }

    private static boolean isVoidLambda(J.Lambda lambda) {
        JavaType.FullyQualified functionalInterface = TypeUtils.asFullyQualified(lambda.getType());
        if (functionalInterface == null) {
            return false;
        }
        for (JavaType.Method method : functionalInterface.getMethods()) {
            if (method.hasFlags(Flag.Abstract)) {
                return method.getReturnType() == JavaType.Primitive.Void;
            }
        }
        return false;
    }

    private boolean isAndMethod(J.MethodInvocation method) {
        if (!"and".equals(method.getSimpleName())) {
            return false;
        }
        if (!(method.getArguments().isEmpty() || method.getArguments().get(0) instanceof J.Empty)) {
            return false;
        }
        // Check return type if available, otherwise check the select's type
        JavaType returnType = method.getType();
        if (returnType != null && TypeUtils.isAssignableTo(securityFqn, returnType)) {
            return true;
        }
        // Fallback: check if the select's method return type matches (for Kotlin where types may not be fully resolved)
        if (method.getSelect() instanceof J.MethodInvocation) {
            JavaType.Method selectMethodType = ((J.MethodInvocation) method.getSelect()).getMethodType();
            if (selectMethodType != null) {
                JavaType selectReturnType = selectMethodType.getReturnType();
                if (selectReturnType != null && TypeUtils.isAssignableTo(securityFqn, selectReturnType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDisableMethod(J.MethodInvocation method) {
        if (new MethodMatcher("org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer disable()", true).matches(method)) {
            return true;
        }
        // Also match reactive disable() methods that return the security builder type (e.g., ServerHttpSecurity.CsrfSpec.disable())
        if ("disable".equals(method.getSimpleName()) &&
                (method.getArguments().isEmpty() || method.getArguments().get(0) instanceof J.Empty)) {
            JavaType returnType = method.getType();
            return returnType != null && TypeUtils.isAssignableTo(securityFqn, returnType);
        }
        return false;
    }

    private J.MethodInvocation createDefaultsCall() {
        JavaType.Method methodType = new JavaType.Method(null, 9, CUSTOMIZER_SHALLOW_TYPE, "withDefaults",
                new JavaType.GenericTypeVariable(null, "T", JavaType.GenericTypeVariable.Variance.INVARIANT, null),
                null, null, null, null, emptyList(), emptyList());
        maybeAddImport(methodType.getDeclaringType().getFullyQualifiedName(), methodType.getName());
        return new J.MethodInvocation(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, null,
                new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "withDefaults", null, null),
                JContainer.empty(), methodType)
                .withSelect(null);
    }

}

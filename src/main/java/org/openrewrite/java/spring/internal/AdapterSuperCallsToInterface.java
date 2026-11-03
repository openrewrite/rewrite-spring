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
package org.openrewrite.java.spring.internal;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

/// For a class that no longer extends a no-op adapter but implements the adapter's interface instead,
/// removes `super(..)` and `super.m(..)` statements, and rewrites a `super.m(..)` used as an expression
/// to `Interface.super.m(..)`, calling the interface's default method.
public class AdapterSuperCallsToInterface extends JavaIsoVisitor<ExecutionContext> {
    private final MethodMatcher adapterMethods;
    private final JavaType.FullyQualified interfaceType;

    public AdapterSuperCallsToInterface(String adapterFqn, String interfaceFqn) {
        this.adapterMethods = new MethodMatcher(adapterFqn + " *(..)");
        this.interfaceType = JavaType.ShallowClass.build(interfaceFqn);
    }

    @Override
    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
        JavaType.Method methodType = mi.getMethodType();
        if (!adapterMethods.matches(methodType) || !isSuperCall(mi)) {
            return mi;
        }
        if (getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
            return null;
        }
        J.Identifier superKeyword = (J.Identifier) mi.getSelect();
        J.FieldAccess interfaceSuper = new J.FieldAccess(
                randomId(),
                superKeyword.getPrefix(),
                Markers.EMPTY,
                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), interfaceType.getClassName(), interfaceType, null),
                JLeftPadded.build(superKeyword.withPrefix(Space.EMPTY).withType(interfaceType)),
                interfaceType);
        JavaType.Method interfaceMethodType = methodType.withDeclaringType(interfaceType);
        return mi.withSelect(interfaceSuper)
                .withMethodType(interfaceMethodType)
                .withName(mi.getName().withType(interfaceMethodType));
    }

    private static boolean isSuperCall(J.MethodInvocation mi) {
        Expression select = mi.getSelect();
        if (select == null) {
            return mi.getMethodType() != null && mi.getMethodType().isConstructor();
        }
        return select instanceof J.Identifier && "super".equals(((J.Identifier) select).getSimpleName());
    }
}

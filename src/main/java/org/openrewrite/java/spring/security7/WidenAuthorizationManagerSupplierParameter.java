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
package org.openrewrite.java.spring.security7;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@Getter
public class WidenAuthorizationManagerSupplierParameter extends Recipe {

    private static final MethodMatcher CHECK = new MethodMatcher(
            "org.springframework.security.authorization.AuthorizationManager check(java.util.function.Supplier, ..)", true);

    final String displayName = "Widen the `Supplier` parameter of `AuthorizationManager#check` overrides";
    final String description = "Spring Security 7 replaces `AuthorizationManager#check(Supplier<Authentication>, T)` with " +
            "`authorize(Supplier<? extends Authentication>, T)`. Renaming an override is not enough for it to override " +
            "`authorize`, so this recipe also changes its parameter to `Supplier<? extends Authentication>`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresMethod<>(CHECK), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (!CHECK.matches(md.getMethodType()) || !(md.getParameters().get(0) instanceof J.VariableDeclarations)) {
                    return md;
                }
                J.VariableDeclarations param = (J.VariableDeclarations) md.getParameters().get(0);
                if (!(param.getTypeExpression() instanceof J.ParameterizedType)) {
                    return md;
                }
                J.ParameterizedType supplier = (J.ParameterizedType) param.getTypeExpression();
                if (supplier.getTypeParameters() == null || supplier.getTypeParameters().size() != 1 ||
                    !(supplier.getTypeParameters().get(0) instanceof TypeTree) ||
                    !TypeUtils.isOfClassType(supplier.getTypeParameters().get(0).getType(), "org.springframework.security.core.Authentication")) {
                    return md;
                }

                TypeTree authentication = (TypeTree) supplier.getTypeParameters().get(0);
                J.Wildcard wildcard = new J.Wildcard(randomId(), authentication.getPrefix(), Markers.EMPTY,
                        JLeftPadded.build(J.Wildcard.Bound.Extends).withBefore(Space.SINGLE_SPACE),
                        authentication.withPrefix(Space.SINGLE_SPACE));
                JavaType.Parameterized widenedType = null;
                if (supplier.getType() instanceof JavaType.Parameterized) {
                    JavaType.Parameterized supplierType = (JavaType.Parameterized) supplier.getType();
                    widenedType = supplierType.withTypeParameters(singletonList(new JavaType.GenericTypeVariable(
                            null, "?", JavaType.GenericTypeVariable.Variance.COVARIANT, singletonList(authentication.getType()))));
                }
                J.ParameterizedType widened = supplier.withTypeParameters(singletonList(wildcard));
                if (widenedType != null) {
                    widened = widened.withType(widenedType);
                }
                JavaType.Parameterized finalType = widenedType;
                J.VariableDeclarations widenedParam = param.withTypeExpression(widened)
                        .withVariables(ListUtils.map(param.getVariables(), v -> finalType == null ? v : v.withType(finalType)
                                .withName(v.getName().withType(finalType))));
                return md.withParameters(ListUtils.mapFirst(md.getParameters(), p -> widenedParam));
            }
        });
    }
}

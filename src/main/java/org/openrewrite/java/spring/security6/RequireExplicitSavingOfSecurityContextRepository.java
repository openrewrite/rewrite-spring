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
package org.openrewrite.java.spring.security6;

import lombok.Getter;
import lombok.Value;
import lombok.With;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;

import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

public class RequireExplicitSavingOfSecurityContextRepository extends Recipe {

    private static final MethodMatcher REQUIRE_EXPLICIT_SAVE_MATCHER =
            new MethodMatcher("org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer#requireExplicitSave(boolean)");
    private static final String HTTP_SECURITY_TYPE = "org.springframework.security.config.annotation.web.builders.HttpSecurity";

    @Getter
    final String displayName = "Remove explicit `SecurityContextConfigurer.requireExplicitSave(true)` opt-in";

    @Getter
    final String description = "Remove explicit `SecurityContextConfigurer.requireExplicitSave(true)` opt-in as that is the new default in Spring Security 6. " +
            "See the corresponding [Sprint Security 6.0 migration step](https://docs.spring.io/spring-security/reference/6.0.0/migration/servlet/session-management.html#_require_explicit_saving_of_securitycontextrepository) for details.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);
                if (method.getSelect() != null && method.getArguments().size() == 1 &&
                        REQUIRE_EXPLICIT_SAVE_MATCHER.matches(method) &&
                        isTrue(method.getArguments().get(0))) {
                    return ToBeRemoved.withMarker(method);
                }
                if (method.getSelect() instanceof J.MethodInvocation && ToBeRemoved.hasMarker(method.getSelect())) {
                    return method.withSelect(((J.MethodInvocation) method.getSelect()).getSelect());
                }
                if (method.getArguments().stream().anyMatch(ToBeRemoved::hasMarker) &&
                        method.getSelect() != null && TypeUtils.isAssignableTo(HTTP_SECURITY_TYPE, method.getSelect().getType())) {
                    if (method.getArguments().stream().allMatch(ToBeRemoved::hasMarker)) {
                        return ToBeRemoved.withMarker(method);
                    }
                    return method.withArguments(method.getArguments().stream().filter(a -> !ToBeRemoved.hasMarker(a)).collect(toList()));
                }
                return method;
            }

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                lambda = super.visitLambda(lambda, ctx);
                J body = lambda.getBody();
                if (body instanceof J.MethodInvocation && ToBeRemoved.hasMarker(body)) {
                    Expression select = ((J.MethodInvocation) body).getSelect();
                    List<J> parameters = lambda.getParameters().getParameters();
                    if (select instanceof J.Identifier && !parameters.isEmpty() && parameters.get(0) instanceof J.VariableDeclarations) {
                        J.VariableDeclarations declarations = (J.VariableDeclarations) parameters.get(0);
                        if (((J.Identifier) select).getSimpleName().equals(declarations.getVariables().get(0).getSimpleName())) {
                            return ToBeRemoved.withMarker(lambda.withBody(J.Block.createEmptyBlock().withPrefix(body.getPrefix())));
                        }
                    } else if (select instanceof J.MethodInvocation) {
                        return lambda.withBody(select.withPrefix(body.getPrefix()));
                    }
                } else if (body instanceof J.Block && ToBeRemoved.hasMarker(body)) {
                    return ToBeRemoved.withMarker(lambda.withBody(J.Block.createEmptyBlock().withPrefix(body.getPrefix())));
                }
                return lambda;
            }

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                block = super.visitBlock(block, ctx);
                List<Statement> statements = block.getStatements();
                if (!statements.isEmpty() && statements.stream().allMatch(ToBeRemoved::hasMarker)) {
                    return ToBeRemoved.withMarker(block.withStatements(emptyList()));
                }
                if (statements.stream().anyMatch(ToBeRemoved::hasMarker)) {
                    //noinspection DataFlowIssue
                    return block.withStatements(statements.stream()
                            .filter(s -> !ToBeRemoved.hasMarker(s) || s instanceof J.MethodInvocation && ((J.MethodInvocation) s).getSelect() instanceof J.MethodInvocation)
                            .map(s -> s instanceof J.MethodInvocation && ToBeRemoved.hasMarker(s) ? ((J.MethodInvocation) s).getSelect().withPrefix(s.getPrefix()) : s)
                            .collect(toList()));
                }
                return block;
            }
        });
    }

    @Value
    @With
    static class ToBeRemoved implements Marker {
        UUID id;

        static <J2 extends J> J2 withMarker(J2 j) {
            return j.withMarkers(j.getMarkers().addIfAbsent(new ToBeRemoved(randomId())));
        }

        static <J2 extends J> J2 removeMarker(J2 j) {
            return j.withMarkers(j.getMarkers().removeByType(ToBeRemoved.class));
        }

        static boolean hasMarker(J j) {
            return j.getMarkers().findFirst(ToBeRemoved.class).isPresent();
        }
    }

    public static boolean isTrue(Expression expression) {
        if (expression instanceof J.Literal) {
            return expression.getType() == JavaType.Primitive.Boolean && Boolean.TRUE.equals(((J.Literal) expression).getValue());
        }
        return false;
    }
}

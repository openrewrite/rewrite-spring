package org.openrewrite.java.spring.mvc;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class JaxrsToSpringmvcResponseEntity extends Recipe {

    @Override
    public @NotNull String getDisplayName() {
        return "Migrate jax-rs Response to spring MVC ResponseEntity";
    }

    @Override
    public @NotNull String getDescription() {
        return "Replaces all jax-rs Response with Spring MVC ResponseEntity.";
    }

    @Override
    public @NotNull Set<String> getTags() {
        return Set.of("Java", "Spring");
    }

    private static final List<MethodMatcher> BUILD_METHOD_MATCHERS = List.of(
            new MethodMatcher("javax.ws.rs.core.Response$ResponseBuilder build()"),
            new MethodMatcher("jakarta.ws.rs.core.Response$ResponseBuilder build()")
    );

    private static final List<MethodMatcher> OK_METHOD_MATCHERS = List.of(
            new MethodMatcher("javax.ws.rs.core.Response ok(..)"),
            new MethodMatcher("jakarta.ws.rs.core.Response ok(..)")
    );

    private static final List<MethodMatcher> ENTITY_METHOD_MATCHERS = List.of(
            new MethodMatcher("javax.ws.rs.core.Response$ResponseBuilder entity(..)"),
            new MethodMatcher("jakarta.ws.rs.core.Response$ResponseBuilder entity(..)")
    );

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                maybeAddImport("org.springframework.http.HttpStatus");
                cu = (J.CompilationUnit) new ChangeType("javax.ws.rs.core.Response.Status", "org.springframework.http.HttpStatus", true)
                        .getVisitor().visitNonNull(cu, ctx);
                cu = (J.CompilationUnit) new ChangeType("jakarta.ws.rs.core.Response.Status", "org.springframework.http.HttpStatus", true)
                        .getVisitor().visitNonNull(cu, ctx);

                doAfterVisit(new ChangeType("javax.ws.rs.core.Response.ResponseBuilder", "org.springframework.http.ResponseEntity.BodyBuilder", true).getVisitor());
                doAfterVisit(new ChangeType("jakarta.ws.rs.core.Response.ResponseBuilder", "org.springframework.http.ResponseEntity.BodyBuilder", true).getVisitor());

                doAfterVisit(new ChangeType("javax.ws.rs.core.Response", "org.springframework.http.ResponseEntity", true).getVisitor());
                doAfterVisit(new ChangeType("jakarta.ws.rs.core.Response", "org.springframework.http.ResponseEntity", true).getVisitor());
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {
                if(matchesAny(BUILD_METHOD_MATCHERS, methodInv)) {
                    List<J.MethodInvocation> chain = new ArrayList<>();
                    J.MethodInvocation current = methodInv;
                    while(current != null) {
                        chain.add(current);
                        current = current.getSelect() instanceof J.MethodInvocation ? (J.MethodInvocation) current.getSelect() : null;
                    }
                    Collections.reverse(chain);
                    Expression body = null;
                    J.MethodInvocation rebuilt = chain.get(0);
                    if(matchesAny(OK_METHOD_MATCHERS, rebuilt) && !rebuilt.getArguments().isEmpty() && !(rebuilt.getArguments().get(0) instanceof J.Empty)) {
                        if(chain.size() > 2) {
                            body = rebuilt.getArguments().get(0);
                            rebuilt = rebuilt.withArguments(Collections.emptyList());
                        } else {
                            return rebuilt.withPrefix(methodInv.getPrefix());
                        }
                    } else if(rebuilt.getSimpleName().equals("serverError")) {
                        rebuilt = rebuilt.withName(rebuilt.getName().withSimpleName("internalServerError"));
                    }
                    for(J.MethodInvocation c : chain.subList(1, chain.size())) {
                        if(matchesAny(BUILD_METHOD_MATCHERS, c) && body != null) {
                            c = c.withName(c.getName().withSimpleName("body")).withArguments(Collections.singletonList(body));
                        }
                        if(matchesAny(ENTITY_METHOD_MATCHERS, c)) {
                            body = c.getArguments().get(0);
                        } else {
                            rebuilt = c.withSelect(rebuilt);
                        }
                    }
                    return rebuilt;
                } else {
                    return super.visitMethodInvocation(methodInv, ctx);
                }
            }

            private static boolean matchesAny(List<MethodMatcher> matchers, J.MethodInvocation mi) {
                for (MethodMatcher m : matchers) {
                    if (m.matches(mi)) {
                        return true;
                    }
                }
                return false;
            }

        };
    }

}

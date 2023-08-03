package org.openrewrite.java.spring.http;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class SimplifyMediaTypeParseCalls extends Recipe {
    @Override
    public String getDisplayName() {
        return "Simplify Unnecessary `MediaType.parse` calls";
    }

    @Override
    public String getDescription() {
        return "Replaces `MediaType.parse('application/json')` with `MediaType.APPLICATION_JSON`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyParseCallsVisitor();
    }

    private static final class SimplifyParseCallsVisitor extends JavaVisitor<ExecutionContext> {
        private final MethodMatcher MEDIATYPE_PARSE_MATCHER = new MethodMatcher("org.springframework.http.MediaType parse(..)");

        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            System.out.println(mi.getSimpleName());
            if (!MEDIATYPE_PARSE_MATCHER.matches(mi) && !mi.getArguments().isEmpty()) {
                return mi;
            }

            J.Literal test = (J.Literal) mi.getArguments().get(0);
            if (!test.getValue().equals("application/json")) {
                return mi;
            }

            return JavaTemplate.builder("MediaType.APPLICATION_JSON")
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace());
        }
    }
}

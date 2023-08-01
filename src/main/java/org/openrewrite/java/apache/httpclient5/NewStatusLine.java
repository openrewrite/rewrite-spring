package org.openrewrite.java.apache.httpclient5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.J;

public class NewStatusLine extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replaces deprecated HttpResponse::getStatusLine()";
    }

    @Override
    public String getDescription() {
        return "HttpResponse::getStatusLine() was deprecated in 4.x, so we replace it for new StatusLine(HttpResponse). " +
                "Ideally we will try to simplify method chains for getStatusCode, getProtocolVersion and getReasonPhrase, " +
                "but there are some scenarios wher ethe StatusLine object is assigned or used directly, and we need to " +
                "instantiate the object.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            final MethodMatcher matcher = new MethodMatcher("org.apache.hc.core5.http.HttpResponse getStatusLine()");
            final JavaTemplate template = JavaTemplate.builder("new StatusLine(#{any(org.apache.hc.core5.http.HttpResponse)})")
                    .javaParser(JavaParser.fromJavaVersion().classpath("httpcore5"))
                    .imports("org.apache.hc.core5.http.message.StatusLine")
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (matcher.matches(m)) {
                    maybeAddImport("org.apache.hc.core5.http.message.StatusLine");
                    return template.apply(updateCursor(m), m.getCoordinates().replace(), m.getSelect());
                }
                return m;
            }
        };
    }
}

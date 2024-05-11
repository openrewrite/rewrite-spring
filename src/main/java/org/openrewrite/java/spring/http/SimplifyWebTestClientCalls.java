package org.openrewrite.java.spring.http;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.template.RecipeDescriptor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J.Literal;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaType;

@RecipeDescriptor(name = "Simplify WebTestClient expressions", description = "Simplifies various types of WebTestClient expressions to improve code readability.")
public class SimplifyWebTestClientCalls extends Recipe {

    @Override
    public String getDisplayName() {
        return "use isOk()";
    }

    @Override
    public String getDescription() {
        return "Replace .isEqualTo(HttpStatusCode.valueOf(200)) with isOk().";
    }

@Override
public JavaIsoVisitor<ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<ExecutionContext>() {

        private final MethodMatcher methodMatcher
            = new MethodMatcher("org.springframework.test.web.reactive.server.StatusAssertions isEqualTo(..)");
        private final JavaType JAVA_TYPE_INT = JavaType.buildType("int");
        private final JavaTemplate isOkTemplate 
            = JavaTemplate.builder("isOk()").build();

        @Override
        public MethodInvocation visitMethodInvocation(MethodInvocation method, ExecutionContext p) {
            if (!methodMatcher.matches(method.getMethodType())) {
                return method;
            }
            Expression expression = method.getArguments().get(0);
            if(expression instanceof Literal) {
                if(JAVA_TYPE_INT.equals(expression.getType())) {
                    Literal literal = (Literal) expression;
                    if(literal.getValue() instanceof Integer) {
                        if ((int) literal.getValue() == 200) {
                            // https://docs.openrewrite.org/concepts-explanations/javatemplate#usage
                            MethodInvocation m = isOkTemplate.apply(getCursor(), method.getCoordinates().replaceMethod());
                            return m;
                        }
                    }
                    return method;
                }
                return method;
            }
            return super.visitMethodInvocation(method, p);
        }
    };
}

}

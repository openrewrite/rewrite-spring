package org.openrewrite.java.spring.batch;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class JobParameterToString extends Recipe {
    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Migration invocation of JobParameter.toString to JobParameter.getValue.toString";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "JobParameter.toString() logic is quite different in spring batch 5, need take JobParameter.getValue.toString replace the JobParameter.toString.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>("org.springframework.batch.core.JobParameter toString()"),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                        method = super.visitMethodInvocation(method, executionContext);
                        if (new MethodMatcher("org.springframework.batch.core.JobParameter toString()").matches(method)) {

                            return JavaTemplate.builder("#{}.getValue().toString()")
                                    .build().apply(getCursor(), method.getCoordinates().replace(), method.getSelect().print(getCursor()));
                        }
                        return method;
                    }
                });
    }
}

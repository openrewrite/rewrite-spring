package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.J;

import java.util.Arrays;

public class ChangeDepricatedHibernateValidationToJavax extends Recipe {

    static String JAVAX_VALIDATION_EXISTS = "javax-validation-exists";

    @Override
    public String getDisplayName() {
        return "Find javax.validation.* usage";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ChangeDepricatedHibernateValidationToJavaxVisitor();
    }

    private class ChangeDepricatedHibernateValidationToJavaxVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
            Arrays.asList("NotEmpty", "NotBlank").stream().forEach(t -> {
                if (!FindTypes.find(cu,"org.hibernate.validator.constraints." + t).isEmpty()) {
                    doAfterVisit(new ChangeType("org.hibernate.validator.constraints." + t, "javax.validation.constraints." + t));
                    executionContext.putMessage(JAVAX_VALIDATION_EXISTS, Boolean.TRUE);
                }
            });
            return super.visitCompilationUnit(cu, executionContext);
        }
    }
}

package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.List;

/**
 * Changes Depricated Hibernate validation constraints to their associated Javax validation variant.
 * <p></p>
 * Then sets the 'javax-validation-exists' ExecutionContext value to True which allows the {@link MaybeAddJavaxValidationDependencies}
 * to add the associated dependencies
 */
public class ChangeDeprecatedHibernateValidationToJavax extends Recipe {

    private static List<String> HIBERNATE_TO_JAVAX_VALIDATION_CONSTRAINTS = Arrays.asList("NotEmpty", "NotBlank");

    @Override
    public String getDisplayName() {
        return "Change Deprecated Hibernate validation constraints to their associated javax variant";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ChangeDepricatedHibernateValidationToJavaxVisitor();
    }

    private class ChangeDepricatedHibernateValidationToJavaxVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
            HIBERNATE_TO_JAVAX_VALIDATION_CONSTRAINTS.stream().forEach(t -> {
                if (!FindTypes.find(cu,"org.hibernate.validator.constraints." + t).isEmpty()) {
                    doAfterVisit(new ChangeType("org.hibernate.validator.constraints." + t, "javax.validation.constraints." + t));
                    executionContext.putMessage(MaybeAddJavaxValidationDependencies.JAVAX_VALIDATION_EXISTS, Boolean.TRUE);
                }
            });
            return super.visitCompilationUnit(cu, executionContext);
        }
    }
}

package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;

public class RequiredFieldIntoConstructorParameter extends Recipe {

    @Override
    public String getDisplayName() {
        return "Required FieldInto Constructor Parameter";
    }

    @Override
    public String getDescription() {
        return "Moves attributes for setters maker with @Required annotation to constructor parameter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

        };
    }
}

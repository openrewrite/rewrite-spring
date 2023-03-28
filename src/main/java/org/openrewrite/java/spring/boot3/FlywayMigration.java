package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;

public class FlywayMigration extends Recipe {
    @Override
    public String getDisplayName() {
        return "todo";
    }


    @Override
    public String getDescription() {
        return "FlywayConfigurationCustomizer beans are now called to customize the FluentConfiguration after any " +
               "Callback and JavaMigration beans have been added to the configuration. An application that defines " +
               "Callback and JavaMigration beans and adds callbacks and Java migrations using a customizer may have " +
               "to be updated to ensure that the intended callbacks and Java migrations are used.\n";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>(){
            // todo
        };
    }
}

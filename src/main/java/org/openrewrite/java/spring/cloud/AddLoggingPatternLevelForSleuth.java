package org.openrewrite.java.spring.cloud;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.spring.AddSpringProperty;

public class AddLoggingPatternLevelForSleuth extends Recipe {
    @Override
    public String getDisplayName() {
        return "Add logging.pattern.level for traceId and spanId";
    }

    @Override
    public String getDescription() {
        return "Add `logging.pattern.level` for traceId and spanId which was previously set by default, if not already set.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        boolean TODO = true; // Limit to projects using Sleuth
        AddSpringProperty addSpringProperty = new AddSpringProperty(
                "logging.pattern.level",
                // The ${spring.application.name:} could not be escaped in yaml so far
                "\"%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]\"",
                "Logging pattern containing traceId and spanId; no longer provided through Sleuth by default",
                null);
        return Preconditions.check(TODO, addSpringProperty.getVisitor());
    }
}

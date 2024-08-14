package org.openrewrite.java.spring.test;

import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Recipe;

public class ExpectedExceptionToAssertThrows extends Recipe {

    @Override
    public @DisplayName String getDisplayName() {
        return "Migrate JUnit 4's ExpectedException";
    }

    @Override
    public @Description String getDescription() {
        return "Replace JUnit 4's ExpectedException with JUnit 5's assertThrows.";
    }
    
}

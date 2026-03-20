package org.openrewrite.java.spring.security7;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class LoadRecipeTest implements RewriteTest {
    @Test
    void run() {
        assertRecipesConfigure("org.openrewrite.java.spring.security7");
    }
}

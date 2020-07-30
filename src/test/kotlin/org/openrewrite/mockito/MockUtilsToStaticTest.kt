package org.openrewrite.mockito

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.assertRefactored

class MockUtilsToStaticTest {
    private val jp = JavaParser.fromJavaVersion()
            .classpath(JavaParser.dependenciesFromClasspath("mockito-all"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun basicInstanceToStaticSwap() {
        val cu = jp.parse("""
            package mockito.example;

            import org.mockito.internal.util.MockUtil;
            
            public class MockitoMockUtils {
                public void isMockExample() {
                    new MockUtil().isMock("I am a real String");
                }
            }
        """.trimIndent())
        val fixed = cu.refactor().visit(MockUtilsToStatic()).fix().fixed

        assertRefactored(fixed, """
            package mockito.example;

            import org.mockito.internal.util.MockUtil;
            
            public class MockitoMockUtils {
                public void isMockExample() {
                    MockUtil.isMock("I am a real String");
                }
            }
        """.trimIndent())
    }

    @Test
    @Disabled("Right now MockUtilsToStatic() is leaving behind a trailing ';' when it removes the 'MockUtil util = new MockUtil();'")
    fun mockUtilsVariableToStatic() {
        val cu = jp.parse("""
            package mockito.example;

            import org.mockito.internal.util.MockUtil;
            
            public class MockitoMockUtils {
                public void isMockExample() {
                    MockUtil util = new MockUtil();
                    util.isMock("I am a real String");
                }
            }
        """.trimIndent())
        val fixed = cu.refactor().visit(MockUtilsToStatic()).fix().fixed

        assertRefactored(fixed, """
            package mockito.example;

            import org.mockito.internal.util.MockUtil;

            public class MockitoMockUtils {
                public void isMockExample() {
                    MockUtil.isMock("I am a real String");
                }
            }
        """.trimIndent())
    }
}
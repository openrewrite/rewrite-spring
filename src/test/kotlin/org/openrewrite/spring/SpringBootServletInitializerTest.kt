package org.openrewrite.spring

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openrewrite.RefactorPlan
import org.openrewrite.java.Java11Parser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class SpringBootServletInitializerTest {
    val jp = Java11Parser.builder()
            .classpath(JavaParser.dependenciesFromClasspath("spring-boot"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun changeType() {
        val a = jp.parse("""
            import org.springframework.boot.web.support.SpringBootServletInitializer;
            
            public class MyApplication extends SpringBootServletInitializer {
            }
        """.trimIndent())

        val plan = RefactorPlan.builder()
                .scanResources()
                .build()

        val fixed = a.refactor()
                .visit(plan.visitors(J::class.java, "spring"))
                .fix().fixed

        assertRefactored(fixed, """
            import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
            
            public class MyApplication extends SpringBootServletInitializer {
            }
        """)
    }
}

package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class SpringBoot2JUnit4to5MigrationTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot-test", "junit", "spring-test")
            .logCompilationWarningsAndErrors(true)
            .build()

    override val recipe: Recipe
        get() = Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes(
                "org.openrewrite.java.spring.boot2.UnnecessarySpringRunWith",
                "org.openrewrite.java.spring.boot2.UnnecessarySpringExtension",
                "org.openrewrite.java.testing.junit5.JUnit4to5Migration"
            )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/43")
    @Test
    fun springBootRunWithRemovedNoExtension() = assertChanged(
        before = """
            package org.springframework.samples.petclinic.system;
            
            import org.junit.Test;
            import org.junit.runner.RunWith;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.springframework.test.context.junit4.SpringRunner;
            
            @SpringBootTest
            @RunWith(SpringRunner.class)
            public class ProductionConfigurationTests {
            
                @Test
                public void testFindAll() {
                }
            }
        """,
        after = """
            package org.springframework.samples.petclinic.system;
            
            import org.junit.jupiter.api.Test;
            import org.springframework.boot.test.context.SpringBootTest;
            
            @SpringBootTest
            public class ProductionConfigurationTests {
            
                @Test
                public void testFindAll() {
                }
            }
        """
    )
}

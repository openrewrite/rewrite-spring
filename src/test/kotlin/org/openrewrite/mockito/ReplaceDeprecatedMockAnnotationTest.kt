package org.openrewrite.mockito

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.spring.assertRefactored

class ReplaceDeprecatedMockAnnotationTest {
    private val jp = JavaParser.fromJavaVersion()
            .classpath(JavaParser.dependenciesFromClasspath("mockito-all"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun replacesAnnotation() {
        val compilationUnit = jp.parse("""
            package mockito.example;
            
            import org.junit.Test;
            import org.mockito.ArgumentCaptor;
            import java.util.List;
            import static org.mockito.Mockito.*;
            
            public class MockitoTests {
                @Test public void usingArgumentCaptorConstructor() {
                    ArgumentCaptor<String> stringArgumentCaptor = new ArgumentCaptor<>();
                }
            }
        """.trimIndent())

        val fixed = compilationUnit.refactor().visit(ArgumentCaptorConstructorToFactory()).fix().fixed

        assertRefactored(fixed, """
            package mockito.example;
            
            import org.junit.Test;
            import org.mockito.ArgumentCaptor;
            import java.util.List;
            import static org.mockito.Mockito.*;
            
            public class MockitoTests {
                @Test public void usingArgumentCaptorConstructor() {
                    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.fromClass(String.class);
                }
            }
        """.trimIndent())
    }
}
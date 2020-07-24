package org.openrewrite.mockito

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.spring.assertRefactored

class ReplaceMatchersWithArgumentMatchersTest {
    private val jp = JavaParser.fromJavaVersion()
            .classpath(JavaParser.dependenciesFromClasspath("mockito-all"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun replacesStaticImports() {
        val compilationUnit = jp.parse("""
            package mockito.example;

            import java.util.List;

            import static org.mockito.Matchers.any;
            import static org.mockito.Matchers.anyBoolean;
            import static org.mockito.Matchers.anyInt;
            import static org.mockito.Matchers.anyList;
            import static org.mockito.Matchers.anyString;
            import static org.mockito.Matchers.eq;
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            
            public class MockitoArgumentMatchersTest {
                static class Foo {
                    boolean bool(String str, int i, Object obj) { return false; }
                    int in(boolean b, List<String> strs) { return 0; }
                    int bar(byte[] bytes, String[] s, int i) { return 0; }
                }

                public void usesMatchers() {
                    Foo mockFoo = mock(Foo.class);
                    when(mockFoo.bool(anyString(), anyInt(), any(Object.class))).thenReturn(true);
                    when(mockFoo.bool(eq("false"), anyInt(), any(Object.class))).thenReturn(false);
                    when(mockFoo.in(anyBoolean(), anyList())).thenReturn(10);
                }
            }
        """.trimIndent())

        val fixed = compilationUnit.refactor().visit(ReplaceMatchersWithArgumentMatchers()).fix().fixed

        assertRefactored(fixed, """
            package mockito.example;

            import java.util.List;

            import static org.mockito.ArgumentMatchers.any;
            import static org.mockito.ArgumentMatchers.anyBoolean;
            import static org.mockito.ArgumentMatchers.anyInt;
            import static org.mockito.ArgumentMatchers.anyList;
            import static org.mockito.ArgumentMatchers.anyString;
            import static org.mockito.ArgumentMatchers.eq;
            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            
            public class MockitoArgumentMatchersTest {
                static class Foo {
                    boolean bool(String str, int i, Object obj) { return false; }
                    int in(boolean b, List<String> strs) { return 0; }
                    int bar(byte[] bytes, String[] s, int i) { return 0; }
                }

                public void usesMatchers() {
                    Foo mockFoo = mock(Foo.class);
                    when(mockFoo.bool(anyString(), anyInt(), any(Object.class))).thenReturn(true);
                    when(mockFoo.bool(eq("false"), anyInt(), any(Object.class))).thenReturn(false);
                    when(mockFoo.in(anyBoolean(), anyList())).thenReturn(10);
                }
            }
        """.trimIndent())
    }

    @Test
    fun replacesNonStaticImports() {
        val compilationUnit = jp.parse("""
            package mockito.example;

            import java.util.List;

            import org.mockito.Matchers.any;
            import org.mockito.Matchers.anyBoolean;
            import org.mockito.Matchers.anyInt;
            import org.mockito.Matchers.anyList;
            import org.mockito.Matchers.anyString;
            import org.mockito.Matchers.eq;
            import org.mockito.Mockito.mock;
            import org.mockito.Mockito.when;
            
            public class MockitoArgumentMatchersTest {
                static class Foo {
                    boolean bool(String str, int i, Object obj) { return false; }
                    int in(boolean b, List<String> strs) { return 0; }
                    int bar(byte[] bytes, String[] s, int i) { return 0; }
                }

                public void usesMatchers() {
                    Foo mockFoo = mock(Foo.class);
                    when(mockFoo.bool(Matchers.anyString(), Matchers.anyInt(), Matchers.any(Object.class))).thenReturn(true);
                    when(mockFoo.bool(Matchers.eq("false"), Matchers.anyInt(), Matchers.any(Object.class))).thenReturn(false);
                    when(mockFoo.in(Matchers.anyBoolean(), Matchers.anyList())).thenReturn(10);
                }
            }
        """.trimIndent())

        val fixed = compilationUnit.refactor().visit(ReplaceMatchersWithArgumentMatchers()).fix().fixed

        assertRefactored(fixed, """
            package mockito.example;

            import java.util.List;

            import org.mockito.ArgumentMatchers.any;
            import org.mockito.ArgumentMatchers.anyBoolean;
            import org.mockito.ArgumentMatchers.anyInt;
            import org.mockito.ArgumentMatchers.anyList;
            import org.mockito.ArgumentMatchers.anyString;
            import org.mockito.ArgumentMatchers.eq;
            import org.mockito.Mockito.mock;
            import org.mockito.Mockito.when;
            
            public class MockitoArgumentMatchersTest {
                static class Foo {
                    boolean bool(String str, int i, Object obj) { return false; }
                    int in(boolean b, List<String> strs) { return 0; }
                    int bar(byte[] bytes, String[] s, int i) { return 0; }
                }

                public void usesMatchers() {
                    Foo mockFoo = mock(Foo.class);
                    when(mockFoo.bool(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(), ArgumentMatchers.any(Object.class))).thenReturn(true);
                    when(mockFoo.bool(ArgumentMatchers.eq("false"), ArgumentMatchers.anyInt(), ArgumentMatchers.any(Object.class))).thenReturn(false);
                    when(mockFoo.in(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyList())).thenReturn(10);
                }
            }
        """.trimIndent())
    }
}
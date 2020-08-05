/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.mockito

import org.junit.jupiter.api.Test
import org.openrewrite.*
import org.openrewrite.java.ChangeMethodName
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

/**
 * Validates the profiles related to upgrading from Mockito 1 to Mockito 3
 */
class MockitoUpgrade1To3Tests() : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("mockito-all", "junit")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = loadVisitors("mockito")

    /**
     * Replace org.mockito.MockitoAnnotations.Mock with org.mockito.Mock
     */
    @Test
    fun replaceMockAnnotation() = assertRefactored(
            before = """
                package mockito.example;
    
                import org.mockito.MockitoAnnotations;
                import java.util.List;
                import static org.mockito.Mockito.verify;
                import static org.mockito.MockitoAnnotations.Mock;
                
                public class MockitoTests {
                
                    @Mock
                    List<String> mockedList;
    
                    public void initMocks() {
                        MockitoAnnotations.initMocks(this);
                    }
                
                    public void usingAnnotationBasedMock() {
                
                        mockedList.add("one");
                        mockedList.clear();
                
                        verify(mockedList).add("one");
                        verify(mockedList).clear();
                    }
                }
            """,
            after = """
                package mockito.example;

                import org.mockito.Mock;
                import org.mockito.MockitoAnnotations;
                
                import java.util.List;
                
                import static org.mockito.*;
                
                public class MockitoTests {
                
                    @Mock
                    List<String> mockedList;
                
                    public void initMocks() {
                        MockitoAnnotations.initMocks(this);
                    }
                
                    public void usingAnnotationBasedMock() {
                
                        mockedList.add("one");
                        mockedList.clear();
                
                        verify(mockedList).add("one");
                        verify(mockedList).clear();
                    }
                }
            """
    )

    /**
     * Replaces org.mockito.Matchers with org.mockito.ArgumentMatchers
     */
    @Test
    fun replacesMatchers() = assertRefactored(
            before = """
                package mockito.example;
    
                import java.util.List;
    
                import static org.mockito.Matchers.*;
                import static org.mockito.Mockito.*;
                
                public class MockitoArgumentMatchersTest {
                    static class Foo {
                        boolean bool(String str, int i, Object obj) { return false; }
                        int in(boolean b, List<String> strs) { return 0; }
                        int bar(byte[] bytes, String[] s, int i) { return 0; }
                        boolean baz(String ... strings) { return true; }
                    }
    
                    public void usesMatchers() {
                        Foo mockFoo = mock(Foo.class);
                        when(mockFoo.bool(anyString(), anyInt(), any(Object.class))).thenReturn(true);
                        when(mockFoo.bool(eq("false"), anyInt(), any(Object.class))).thenReturn(false);
                        when(mockFoo.in(anyBoolean(), anyList())).thenReturn(10);
                    }
                }
            """,
            after = """
                package mockito.example;
    
                import java.util.List;
                
                import static org.mockito.ArgumentMatchers.*;
                import static org.mockito.Mockito.*;
                
                public class MockitoArgumentMatchersTest {
                    static class Foo {
                        boolean bool(String str, int i, Object obj) { return false; }
                        int in(boolean b, List<String> strs) { return 0; }
                        int bar(byte[] bytes, String[] s, int i) { return 0; }
                        boolean baz(String ... strings) { return true; }
                    }
                
                    public void usesMatchers() {
                        Foo mockFoo = mock(Foo.class);
                        when(mockFoo.bool(anyString(), anyInt(), any(Object.class))).thenReturn(true);
                        when(mockFoo.bool(eq("false"), anyInt(), any(Object.class))).thenReturn(false);
                        when(mockFoo.in(anyBoolean(), anyList())).thenReturn(10);
                    }
                }
            """
    )

    /**
     * Mockito 1 used Matchers.anyVararg() to match the arguments to a variadic function.
     * Mockito 2+ uses Matchers.any() to match anything including the arguments to a variadic function.
     */
    @Test
    fun replacesAnyVararg() = assertRefactored(
            before = """
                package mockito.example;
    
                import static org.mockito.Matchers.anyVararg;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;
                
                public class MockitoVarargMatcherTest {
                    public static class Foo {
                        public boolean acceptsVarargs(String ... strings) { return true; }
                    }
                    public void usesVarargMatcher() {
                        Foo mockFoo = mock(Foo.class);
                        when(mockFoo.acceptsVarargs(anyVararg())).thenReturn(true);
                    }
                }
            """,
            after = """
                package mockito.example;

                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;
                
                public class MockitoVarargMatcherTest {
                    public static class Foo {
                        public boolean acceptsVarargs(String ... strings) { return true; }
                    }
                    public void usesVarargMatcher() {
                        Foo mockFoo = mock(Foo.class);
                        when(mockFoo.acceptsVarargs(any())).thenReturn(true);
                    }
                }
            """
    )

    /**
     * Mockito 1 has InvocationOnMock.getArgumentAt(int, Class)
     * Mockito 3 has InvocationOnMock.getArgument(int, Class)
     * swap 'em
     */
    @Test
    fun replacesGetArgumentAt() = assertRefactored(
            before = """
                package mockito.example;

                import org.junit.jupiter.api.Test;
                
                import static org.mockito.Matchers.any;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;
                
                public class MockitoDoAnswer {
                    @Test
                    public void aTest() {
                        String foo = mock(String.class);
                        when(foo.concat(any())).then(invocation -> invocation.getArgumentAt(0, String.class));
                    }
                }
            """,
            after = """
                package mockito.example;

                import org.junit.jupiter.api.Test;
                
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.when;
                
                public class MockitoDoAnswer {
                    @Test
                    public void aTest() {
                        String foo = mock(String.class);
                        when(foo.concat(any())).then(invocation -> invocation.getArgument(0, String.class));
                    }
                }
            """
    )
}
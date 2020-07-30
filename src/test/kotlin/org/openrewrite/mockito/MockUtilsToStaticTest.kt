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
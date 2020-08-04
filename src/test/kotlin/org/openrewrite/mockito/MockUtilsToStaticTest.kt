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

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J
import org.openrewrite.loadVisitors

class MockUtilsToStaticTest(
        override val parser: JavaParser = JavaParser.fromJavaVersion()
                .classpath("mockito-all")
                .build(),
        override val visitors: Iterable<RefactorVisitor<*>> = loadVisitors("mockito")
) : RefactorVisitorTestForParser<J.CompilationUnit> {

    @Test
    fun basicInstanceToStaticSwap() = assertRefactored(
            before = """
                package mockito.example;
    
                import org.mockito.internal.util.MockUtil;
                
                public class MockitoMockUtils {
                    public void isMockExample() {
                        new MockUtil().isMock("I am a real String");
                    }
                }
            """,
            after = """
                package mockito.example;
    
                import org.mockito.internal.util.MockUtil;
                
                public class MockitoMockUtils {
                    public void isMockExample() {
                        MockUtil.isMock("I am a real String");
                    }
                }
            """
    )

    @Test
    @Disabled("Right now MockUtilsToStatic() is leaving behind a trailing ';' when it removes the 'MockUtil util = new MockUtil();'")
    fun mockUtilsVariableToStatic() = assertRefactored(
            before = """
                package mockito.example;
    
                import org.mockito.internal.util.MockUtil;
                
                public class MockitoMockUtils {
                    public void isMockExample() {
                        MockUtil util = new MockUtil();
                        util.isMock("I am a real String");
                    }
                }
            """,
            after = """
                package mockito.example;
    
                import org.mockito.internal.util.MockUtil;
    
                public class MockitoMockUtils {
                    public void isMockExample() {
                        MockUtil.isMock("I am a real String");
                    }
                }
            """
    )
}